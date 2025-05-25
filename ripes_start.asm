# Copyright (c) 2025 Prev25 Wizards
.text
_start: # real entrypoint of asm prg
    # set some regs
    li gp, 0 # should be data
    li tp, 0x0000000100000000 # heap
    li sp, 0x00000000FFFFFFF0 # stack
    mv s0, sp # fp = s0
    call _main  # jump to target and save position to ra
    ld a0, 0(sp)
    li a7, 93
    ecall
