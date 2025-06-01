## Standard library for PREV'25

### I/O

- `puts(str: ^char): void` - Print string
- `putint(x: int): void` - Print signed integer
- `putchar(c: char): void` - Print character
- `getchar(): char` - Read character
- `gets(buf: ^char, size: int): int` - Read line into buffer
- `getint(): int` - Read integer (skips whitespace)

### Number Formats

- `putint_hex(x: int): void` - Print as hex
- `putuint(x: int): void` - Print as unsigned
- `putint_bin(x: int): void` - Print as binary

### Memory

- `new(size: int): ^any` / `malloc(size: int): ^any` - Allocate memory
- `del(ptr: ^any): void` / `free(ptr: ^any): void` - Free memory (no-op)

### Arrays

- `at(ptr: ^any, idx: int): ^any` - Array access (8-byte elements)
- `at_sys(ptr: ^any, idx: int, size_of_any: int): ^any` - Array access (custom size)

### Random

- `random(lower: int, upper: int): int` - Random int in range
- `seed(value: int): void` - Set RNG seed

### Formatted

- `printf_sys(format: ^char, ...): void` - Formatted print (basic: %d, %c, %s, %%)

## Syscall Codes and Arguments for Ripes / RV64M

### Register Mapping (RISC-V calling convention)

- **a0** = x10 = first argument (`arg0`)
- **a1** = x11 = second argument (`arg1`)
- **a2** = x12 = third argument (`arg2`)
- **a3** = x13 = fourth argument (`arg3`)
- **a4** = x14 = fifth argument (`arg4`)
- **a5** = x15 = sixth argument (`arg5`)
- **a6** = x16 = seventh argument (`arg6`)
- **a7** = x17 = syscall code

### Syscall Table for Ripes

| Name             | Code | Arguments (by register)                                                   | Returns (in a0)                     | Description                    |
| ---------------- | ---- | ------------------------------------------------------------------------- | ----------------------------------- | ------------------------------ |
| PrintInt         | 1    | a0: integer to print                                                      | —                                   | Prints an integer              |
| PrintFloat       | 2    | a0: float to print                                                        | —                                   | Prints a floating-point number |
| PrintStr         | 4    | a0: address of null-terminated string                                     | —                                   | Prints a string                |
| Exit             | 10   | a0: exit code                                                             | —                                   | Exits program                  |
| PrintChar        | 11   | a0: character to print (lowest byte used)                                 | —                                   | Prints a single character      |
| GetCWD           | 17   | a0: buffer address<br>a1: buffer length                                   | a0: 0 on success, -1 on error       | Writes CWD path into buffer    |
| TimeMs           | 30   | —                                                                         | a0: time in milliseconds            | Returns current time in ms     |
| Cycles           | 31   | —                                                                         | a0: cycle count                     | Returns current cycle count    |
| PrintIntHex      | 34   | a0: integer to print                                                      | —                                   | Prints an integer in hex       |
| PrintIntBinary   | 35   | a0: integer to print                                                      | —                                   | Prints an integer in binary    |
| PrintIntUnsigned | 36   | a0: integer to print                                                      | —                                   | Prints an unsigned integer     |
| Close            | 57   | a0: file descriptor                                                       | a0: 0 on success, -1 on error       | Closes a file                  |
| LSeek            | 62   | a0: file descriptor<br>a1: offset<br>a2: whence                           | a0: new offset, -1 on error         | Seeks in a file                |
| Read             | 63   | a0: file descriptor<br>a1: buffer address<br>a2: max bytes to read        | a0: number of bytes read, -1 on err | Reads from a file descriptor   |
| Write            | 64   | a0: file descriptor<br>a1: buffer address<br>a2: number of bytes to write | a0: number of bytes written         | Writes to a file descriptor    |
| FStat            | 80   | a0: file descriptor<br>a1: address of struct stat                         | a0: 0 on success, -1 on error       | Gets info about a file         |
| Exit2            | 93   | a0: exit code                                                             | —                                   | Exits program (alternate)      |
| brk              | 214  | a0: address                                                               | a0: 0 on success, -1 on error       | Increases program data space   |
| Open             | 1024 | a0: pointer to path<br>a1: flags                                          | a0: file descriptor or -1 on error  | Opens a file                   |

**References:**

- [Syscall codes and argument lists](https://github.com/mortbopet/Ripes/blob/03df996b0cc6c7f981b58bbd73363f7235576a08/src/isa/rvisainfo_common.h)
- [Syscall class and documentation](https://github.com/mortbopet/Ripes/blob/03df996b0cc6c7f981b58bbd73363f7235576a08/src/syscall/file.h)
- [RISC-V calling convention](https://riscv.org/wp-content/uploads/2015/01/riscv-calling.pdf)
