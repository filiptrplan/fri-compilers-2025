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
cp lib prev25/lib -r

rm prev25/lib/*.jar
LEXER_AUTOGEN='prev25/src/compiler/phase/lexan/Prev25Lexer'
rm $LEXER_AUTOGEN.interp $LEXER_AUTOGEN.java $LEXER_AUTOGEN.tokens

mkdir prev25/prg
cp prg/Makefile prev25/prg/

zip -r $VPISNA-$1.zip prev25
rm prev25 -r
