#!/bin/sh
set -e;

WATCH=0;
PROJ="";
FILTERS="";
USE_DOCKER=0;
RUNNING_IN_PIPELINE=0;
RUN_LOCAL_ENV=0;
TEST_TYPE="";
COVERAGE="";

while [ "$#" -gt 0 ]; do
  case "$1" in
    -w|--watch) WATCH=1; shift 1;;
    --docker) USE_DOCKER=1; shift 1;;
    --cicd) RUNNING_IN_PIPELINE=1; USE_DOCKER=1; shift 1;;
    --filter) FILTERS="${2}"; shift 2;;
    --unit) FILTERS="-DtestTags=Unit"; TEST_TYPE="unit"; shift 1;;
    --integration) FILTERS="-DtestTags=Integration"; TEST_TYPE="integration"; RUN_LOCAL_ENV=1; USE_DOCKER=1; shift 1;;
    --e2e) FILTERS="-DtestTags=E2E"; TEST_TYPE="e2e"; RUN_LOCAL_ENV=1; USE_DOCKER=1; shift 1;;
    --coverage) COVERAGE="-Pcoverage"; FILTERS="-DtestTags=Unit"; TEST_TYPE="unit"; shift 1;;

    -*) echo "unknown option: $1" >&2; exit 1;;
    *) PROJ=$1; shift 1;;
  esac
done

if [ $RUN_LOCAL_ENV -eq 1 ]; then
  if [ -f ./setup/local/.env.test ]; then
    echo "Loading overrides from ./setup/local/.env.test";
    set -a;
    . ./setup/local/.env.test;
    set +a;
  fi

  START_ARGS="-d";
  if [ $RUNNING_IN_PIPELINE -eq 1 ]; then
    START_ARGS="${START_ARGS} --cicd";
  fi

  sh ./cli/start.sh $START_ARGS;

  echo "Waiting for all Docker services to be healthy or up...";

  MAX_RETRIES=30;
  RETRY_DELAY=2;
  ATTEMPTS=0;

  COMPOSE_FILE="./setup/local/docker-compose.yml";
  PROJECT_NAME="myapp";

  while : ; do
    # List any containers that are not ready yet.
    # Rule: OK if .State == "running" and (no healthcheck OR Health == "healthy").
    # Ignore the one-shot db_init service.
    BAD_CONTAINERS=$(
      docker compose -f "${COMPOSE_FILE}" -p "${PROJECT_NAME}" ps --format '{{json .}}' \
      | jq -r '
          # ignore one-shot
          select((.Service // .Name) != "db_init")
          # not running OR (has healthcheck and not healthy)
          | select(
              (.State != "running")
              or
              ((.Health? // "") as $h | ($h != "" and $h != "healthy"))
            )
          | "\((.Service // .Name)): \(.State) (\(.Health // "no healthcheck"))"
        '
    );

    if [ -z "${BAD_CONTAINERS}" ]; then
      echo "All services are up and (if defined) healthy!";
      docker compose -f "${COMPOSE_FILE}" -p "${PROJECT_NAME}" ps;
      break;
    fi

    ATTEMPTS=$((ATTEMPTS+1));
    if [ ${ATTEMPTS} -ge ${MAX_RETRIES} ]; then
      echo "ERROR: Some services failed to become ready:";
      echo "${BAD_CONTAINERS}";
      echo "Recent logs (last 200 lines each):";
      docker compose -f "${COMPOSE_FILE}" -p "${PROJECT_NAME}" logs --tail=200;
      exit 1;
    fi

    sleep $RETRY_DELAY;
  done
else
  docker network create myapp_shared || true;
fi

PROJ_ARGS="";
if [ -n "$PROJ" ]; then
  PROJ_ARGS="-pl ${PROJ} -am";
fi

CMD="mvn -B ${PROJ_ARGS} test ${FILTERS} ${COVERAGE}";

if [ $WATCH -eq 1 ]; then
  if [ -z "$PROJ" ]; then
    echo "In watch mode a project name or path must be provided as argument." >&2; exit 1;
  fi

  if ! command -v entr >/dev/null 2>&1; then
    echo "In watch mode you need 'entr' installed (e.g., apt-get install entr / brew install entr)." >&2; exit 1;
  fi

  echo "Watch mode enabled. Re-running on changes under ${PROJ}/src ...";
  find "${PROJ}/src/main" "${PROJ}/src/test" -type f \( -name "*.java" -o -name "*.xml" -o -name "*.properties" -o -name "*.yml" -o -name "*.yaml" \) | entr -r /bin/sh -lc "${CMD}";

  exit 0;
fi

if [ $USE_DOCKER -eq 1 ]; then
  INTERACTIVE_FLAGS="-it";
  if [ $RUNNING_IN_PIPELINE -eq 1 ]; then
    INTERACTIVE_FLAGS="-i";
  fi

  docker run --rm ${INTERACTIVE_FLAGS} --name myapp_test_runner --network=myapp_shared -v "./:/app/" -w "/app/" maven:3.9-eclipse-temurin-21 /bin/sh -lc "${CMD}";

  if [ $RUNNING_IN_PIPELINE -eq 0 ]; then
    find src -type d -name target -prune -exec rm -rf {} \; 2>/dev/null || true;
  fi
else
  eval "${CMD}";
fi
