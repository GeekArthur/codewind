#!/bin/bash
#
#*******************************************************************************
# Copyright (c) 2019 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v2.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v20.html
#
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************

# When this script is called from CI/Travis, parameters are userid and password for artifactory
USERNAME=$1;
PASSWORD=$2;
DIR=`pwd`;
SRC_DIR=$DIR/src;
PFE=pfe
INITIALIZE=initialize
PERFORMANCE=performance;
ARCH=`uname -m`;
TAG=latest;
REGISTRY=eclipse

# On intel, uname -m returns "x86_64", but the convention for our docker images is "amd64"
if [ "$ARCH" == "x86_64" ]; then
  IMAGE_ARCH="amd64"
else
  IMAGE_ARCH=$ARCH
fi

ALL_IMAGES="$PFE $PERFORMANCE $INITIALIZE";

# Copy .env over to file-watcher
if [ -f $DIR/.env ]; then
  echo -e "\nCopying $DIR/.env to ${SRC_DIR}/${PFE}/file-watcher/scripts/.env\n"
  cp $DIR/.env ${SRC_DIR}/${PFE}/file-watcher/scripts/.env
fi

# Copy the license files to the portal, performance, initialize
cp -r $DIR/LICENSE.md ${SRC_DIR}/pfe/portal/
cp -r $DIR/NOTICE.md ${SRC_DIR}/pfe/portal/
cp -r $DIR/LICENSE ${SRC_DIR}/initialize/
cp -r $DIR/NOTICE.md ${SRC_DIR}/initialize/
cp -r $DIR/LICENSE.md ${SRC_DIR}/performance/
cp -r $DIR/NOTICE.md ${SRC_DIR}/performance/

# Copy the docs into portal
cp -r $DIR/docs ${SRC_DIR}/pfe/portal/

# BUILD IMAGES
# Uses a build file in each of the directories that we want to use
echo -e "\n+++   BUILDING DOCKER IMAGES   +++\n";

for image in $ALL_IMAGES
do
  export IMAGE_NAME=codewind-$image-$IMAGE_ARCH
  echo Building image $IMAGE_NAME;
  cd ${SRC_DIR}/${image};
  time sh build Dockerfile_${ARCH};

  if [ $? -eq 0 ]; then
    echo "+++   SUCCESSFULLY BUILT $IMAGE_NAME   +++";
    fi
 done;
echo -e "\n+++   ALL DOCKER IMAGES SUCCESSFULLY BUILT   +++\n";
docker images | grep codewind;