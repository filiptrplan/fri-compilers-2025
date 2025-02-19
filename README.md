# FRI Compilers 2025

This is my implementation of the Prev25 language made for the Compilers
course at FRI, University of Ljubljana. The specification for the
language is located at [spec.pdf](./spec.pdf).

## Project structure

- `src`: This is where the source code of the compiler lives
  - `src/compiler/common`: Holds the code common to all phases.
  - `src/compiler/phases`: Holds the phases of the compiler.
- `lib`: Where we have the additional libraries we will be using
- `prg`: Programs in Prev25 for testing.

## Running and building

This project uses `make` as it's build system. To build:

```bash
make
```

To test a program, go into the `prg` directory and run:

```bash
make <program name>
```
