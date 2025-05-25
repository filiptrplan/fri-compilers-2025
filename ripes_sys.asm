# Copyright 2025 Prev25 Wizards
.text # Wrappers for RIPES system calls

# Exit Program
.align 4
.global _die
_die:
    li a7, 10
    ecall

# Exit Program with code
.align 4
.global _exit
_exit:
    ld a0, 8(sp) # exit code
    li a7, 93
    ecall

# Print Integer
.align 4
.global _putint
_putint:
    # save regs
    sd a0, -8(sp)
    sd a7, -16(sp)

    # actual call
    ld a0, 8(sp)
    li a7, 1
    ecall

    # restore regs
    ld a0, -8(sp)
    ld a7, -16(sp)

    ret

# Print String
.align 4
.global _puts
_puts:
    # save regs
    sd a0, -8(sp)
    sd a7, -16(sp)

    # actual call
    ld a0, 8(sp)
    li a7, 4
    ecall

    # restore regs
    ld a0, -8(sp)
    ld a7, -16(sp)

    ret

# Print Character
.align 4
.global _putchar
_putchar:
    # save regs
    sd a0, -8(sp)
    sd a7, -16(sp)

    # actual call
    ld a0, 8(sp)
    li a7, 11
    ecall

    # restore regs
    ld a0, -8(sp)
    ld a7, -16(sp)

    ret

# Print Integer as Hex
.align 4
.global _putint_hex
_putint_hex:
    # save regs
    sd a0, -8(sp)
    sd a7, -16(sp)

    # actual call
    ld a0, 8(sp)
    li a7, 34
    ecall

    # restore regs
    ld a0, -8(sp)
    ld a7, -16(sp)

    ret

# Print Integer as Binary
.align 4
.global _putint_bin
_putint_bin:
    # save regs
    sd a0, -8(sp)
    sd a7, -16(sp)

    # actual call
    ld a0, 8(sp)
    li a7, 35
    ecall

    # restore regs
    ld a0, -8(sp)
    ld a7, -16(sp)

    ret

# Print Integer as Unsigned
.align 4
.global _putuint
_putuint:
    # save regs
    sd a0, -8(sp)
    sd a7, -16(sp)

    # actual call
    ld a0, 8(sp)
    li a7, 36
    ecall

    # restore regs
    ld a0, -8(sp)
    ld a7, -16(sp)

    ret

# obtains char from stdin
.align 4
.global _getchar
_getchar:
  # save regs
  sd a0, -8(sp)
  sd a1, -16(sp)
  sd a2, -24(sp)
  sd a7, -32(sp)

  li a7, 63 # read sys call
  li a0, 0 # stdin
  mv a1, sp # out buf is actually reg
  sd x0, 0(a1) # zero out buf
  li a2, 1 # num of char read
  ecall

  # restore regs
  ld a0, -8(sp)
  ld a1, -16(sp)
  ld a2, -24(sp)
  ld a7, -32(sp)

  ret


# obtains string from stdin
.align 4
.global _gets # (ptr, len) -> bytes read
_gets:
  # save regs
  sd a0, -8(sp)
  sd a1, -16(sp)
  sd a2, -24(sp)
  sd a7, -32(sp)

  li a7, 63 # read sys call
  li a0, 0 # stdin
  ld a1, 8(sp) # ptr
  ld a2, 16(sp) # max buf read
  addi a2, a2, -1 # for nul byte
  ecall

  add a1, a1, a0
  sb x0, 1(a1)
  sd a0, 0(sp)

  # restore regs
  ld a0, -8(sp)
  ld a1, -16(sp)
  ld a2, -24(sp)
  ld a7, -32(sp)

  ret

# Read a 64-bit signed integer from stdin, char by char
.align 4
.global _getint
_getint:
  # save regs
  sd a0, -8(sp)
  sd a1, -16(sp)
  sd a2, -24(sp)
  sd a7, -32(sp)
  sd t0, -40(sp)
  sd t1, -48(sp)
  sd t2, -56(sp)
  sd t3, -64(sp)

  mv t2, x0
  mv t3, x0 # sign flag

maybe_read_empty:
  li a7, 63 # read sys call
  li a0, 0 # stdin
  mv a1, sp # out buf is actually reg
  sd x0, 0(a1) # zero out buf
  li a2, 1 # num of char read
  ecall

  blt a0, x0, EOF
  lb t0, 0(sp)

parse_empty:
  li t1, 32 # space
  beq t0, t1, maybe_read_empty
  li t1, 13 # \r
  beq t0, t1, maybe_read_empty
  li t1, 10 # \n
  beq t0, t1, maybe_read_empty
  li t1, 9 # \t
  beq t0, t1, maybe_read_empty

parse_sign:
# +, -
  addi t1, t0, -45 # -
  seqz t3, t1
  beqz t1, read
  addi t1, t0, -43
  bnez t1, parse_int

read:
  li a7, 63 # read sys call
  li a0, 0 # stdin
  mv a1, sp # out buf is actually reg
  sd x0, 0(a1) # zero out buf
  li a2, 1 # num of char read
  ecall
  # a0 len
  # res=0(sp)
  blt a0, x0, EOF
  lb t0, 0(sp)
parse_int:
  slti t1, t0, 48
  bnez t1, EOF
  slti t1, t0, 57
  beqz t1, EOF
  addi t0, t0, -48
  li t1, 10
  mul t2, t2, t1
  add t2, t2, t0
  j read

EOF:
  beqz t3, STORE
  neg t2, t2
STORE:
  sd t2, 0(sp)
  # restore regs
  ld a0, -8(sp)
  ld a1, -16(sp)
  ld a2, -24(sp)
  ld a7, -32(sp)
  ld t0, -40(sp)
  ld t1, -48(sp)
  ld t2, -56(sp)
  ld t3, -64(sp)

  ret

.align 4
.global _new #(size in bytes) -> ptr
_new:
.align 4
.global _malloc #(size in bytes) -> ptr
_malloc:
  # save regs
  sd a0, -8(sp)

  sd tp, 0(sp) # store results
  ld a0, 8(sp)
  add tp, tp, a0
  andi tp, tp, -8 # ensure that it is aligned to 8
  addi tp, tp, 8

  # restore
  ld a0, -8(sp)

  ret

# free allocated
.align 4
.global _del
_del:
.align 4
.global _free
_free:
  # nop
  ret
