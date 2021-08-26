#!/usr/bin/env bash

now_dir=`pwd`
cd `dirname $0`

shell_dir=`pwd`

mvn clean -Dmaven.test.skip=true package appassembler:assemble


chmod +x target/bakuptool-release/bin/BackupTool.sh

cd target/bakuptool-release

zip -r bakuptool-release.zip ./*

mv bakuptool-release.zip ../


cd ${now_dir}