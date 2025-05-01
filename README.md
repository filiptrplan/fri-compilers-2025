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

You can also use the `run.py` runner to compile the compiler and test a program.
For usage instructions run:

```bash
./run.py --help
```

## Implementation details

Here I document some implementation details not given in the specification of the language.

### Call frames and memory layout

The call frame is structured like this

```
+-------------------------------------+ <-- Higher memory address
| Parameters and return value of call |
+-------------------------------------+ <-- FP
| Local variables                     |
| ...                                 |
+-------------------------------------+
| Saved FP from the parent call       |
+-------------------------------------+
| Return address                      |
+-------------------------------------+
| Temporary variables                 |
| ...                                 |
+-------------------------------------+
| Saved registers                     |
| ...                                 |
+-------------------------------------+
| Parameters and return values of     |
| outgoing calls                      |
+-------------------------------------+  <-- SP
        |
        | Stack grows down
        V
```

Some notes on this memory layout and calling convention:

- Arguments are always passed on the stack. Earlier arguments have lower offsets from the `FP`.
- Return values are also return with the stack. A function writes its return value just above the `FP`.
- We use a static link (`SL`) for nested functions and we always pass it as an "extra" argument
  to a function on the offset 0.

### IMC Linearization

After generating the IMC we perform linearization in this gives us certain gurantees on how the IMC
is structured in later phases. The most important one is that arguments of the `IMC.CALL` instructions
are guranteed to be `IMC.TEMP`s and nothing else. The linearization generator makes sure that all the arguments
are calculated in the right order to preserve how side-effects influence one another.
