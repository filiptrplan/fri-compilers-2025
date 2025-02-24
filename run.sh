#!/bin/bash
make
DEBUG=true make -C prg $1
