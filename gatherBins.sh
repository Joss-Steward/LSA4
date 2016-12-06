#!/bin/bash

echo "Cleaning up existing files"
rm bin/FileSystem.idl
rm -rv bin/FileSystemApp/
rm -rv bin/orb.db/

echo "Copying in new files"
cp -r FileSystem/bin/* bin/
cp FileSystemClient/bin/FileSystemApp/* bin/FileSystemApp/
cp FileSystemServer/bin/FileSystemApp/* bin/FileSystemApp/

echo "All done"
