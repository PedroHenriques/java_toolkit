#!/bin/sh
set -e;

UPDATE=0;
UPDATE_PROMPT=1;
USE_DOCKER=0;
RUNNING_IN_PIPELINE=0;

while [ "$#" -gt 0 ]; do
  case "$1" in
    -u|--update) UPDATE=1; shift 1;;
    -y) UPDATE_PROMPT=0; shift 1;;
    --docker) USE_DOCKER=1; shift 1;;
    --cicd) RUNNING_IN_PIPELINE=1; USE_DOCKER=1; shift 1;;

    -*) echo "unknown option: $1" >&2; exit 1;;
  esac
done

CMD="mvn -B versions:display-dependency-updates -DprocessAllModules=true -DprocessParent=true -DprocessPlugins=true";
if [ $UPDATE -eq 1 ]; then
  if [ $UPDATE_PROMPT -eq 1 ]; then
    echo "This will update dependency/plugin/parent versions in your pom.xml files";
    echo "using the Maven Versions Plugin (use-latest-releases).";
    echo "No backup POMs will be created. Continue? [y/N]";
    read ans;
    case "$ans" in
      y|Y|yes|YES) ;;
      *) echo "Aborted."; exit 1;;
    esac
  fi

  CMD="mvn -B versions:use-latest-releases -DprocessAllModules=true -DprocessParent=true -DprocessPlugins=true -DgenerateBackupPoms=false";
fi

if [ $USE_DOCKER -eq 1 ]; then
  INTERACTIVE_FLAGS="-it";
  if [ $RUNNING_IN_PIPELINE -eq 1 ]; then
    INTERACTIVE_FLAGS="-i";
  fi

  docker run --rm ${INTERACTIVE_FLAGS} -v "./:/app/" -w "/app/" maven:3.9-eclipse-temurin-21 /bin/sh -c "${CMD}";
else
  eval "${CMD}";
fi
