#!/bin/bash

VPISNA='63220340'

if [ -z "$1" ]; then
  echo "Error: First argument must be the phase name."
  exit 1
fi

rm $VPISNA-$1.zip
rm prev25 -r

mkdir prev25
cp src prev25/src -r
zip -r $VPISNA-$1.zip prev25
rm prev25 -r
