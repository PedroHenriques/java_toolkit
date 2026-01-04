#!/bin/sh
set -e;

: "${TEST_COVERAGE_DIR_PATH:=coverageReport}";

USE_DOCKER=0;
RUNNING_IN_PIPELINE=0;
DOCKER_FLAG="";
CICD_FLAG="";

while [ "$#" -gt 0 ]; do
  case "$1" in
    --docker) USE_DOCKER=1; shift 1;;
    --cicd) RUNNING_IN_PIPELINE=1; USE_DOCKER=1; shift 1;;

    -*) echo "unknown option: $1" >&2; exit 1;;
  esac
done

if [ $USE_DOCKER -eq 1 ]; then
  DOCKER_FLAG="--docker";
fi
if [ $RUNNING_IN_PIPELINE -eq 1 ]; then
  CICD_FLAG="--cicd";
fi

if [ $USE_DOCKER -eq 0 ]; then
  rm -rf ./${TEST_COVERAGE_DIR_PATH};
fi

find . -type f -name "jacoco.exec" -delete 2>/dev/null || true;
find . -type d -path "*/target/site/jacoco" -prune -exec rm -rf {} \; 2>/dev/null || true;

sh ./cli/test.sh --coverage $DOCKER_FLAG $CICD_FLAG;

mkdir -p "./${TEST_COVERAGE_DIR_PATH}";

# Copy every module's JaCoCo HTML report
for report in $(find . -type f -path "*/target/site/jacoco/index.html" 2>/dev/null); do
  mod="$(echo "$report" | sed 's#^\./##' | sed 's#/target/site/jacoco/index.html##')";
  out="./${TEST_COVERAGE_DIR_PATH}/${mod}";
  mkdir -p "$out";
  cp -R "$(dirname "$report")"/* "$out"/;
done
