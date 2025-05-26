package compiler.phase.asmgen;

import java.util.ArrayList;
import java.util.List;

import compiler.common.report.Report;

public class SyscallWrappers {

	/** A syscall wrapper */
	public static class Wrapper {
		/** The name of the function wrapped */
		private final String wrapperName;

		/** The body of the syscall */
		private final String body;

		/** The alias of the function wrapped */
		public String alias;

		/** Registers to save and restore after the syscall */
		private final List<String> saveRegisters;

		public Wrapper(String wrappedFunctionName, String body, String... registers) {
			this.wrapperName = wrappedFunctionName;
			this.body = body;
			this.saveRegisters = new ArrayList<>();

			for (String reg : registers) {
				if ("x2".equals(reg) || "x1".equals(reg)) {
					throw new Report.InternalError();
				}
				this.saveRegisters.add(reg);
			}

			if (!this.saveRegisters.contains("a7")) {
				this.saveRegisters.add("a7");
			}
		}

		@Override
		public String toString() {
			String output = "";
			output += "_" + wrapperName + ":\n";
			if (alias != null) {
				// Add a nop to make sure both the main name and the alias are in the same place
				output += "\tnop\n";
				output += "_" + alias + ":\n";
			}

			if (saveRegisters != null) {
				long offset = 8;
				for (String register : saveRegisters) {
					output += String.format("sd %s, %d(x2)\n", register, -offset);
					offset += 8;
				}
			}

			output += body + "\n";

			if (saveRegisters != null) {
				long offset = 8;
				for (String register : saveRegisters) {
					output += String.format("ld %s, %d(x2)\n", register, -offset);
					offset += 8;
				}
			}

			output += "jalr x0, x1, 0\n";

			return output;
		}
	}

	public static List<Wrapper> getSyscallWrappers() {
		List<Wrapper> wrappers = new ArrayList<>();

		// fun puts(str: ^char): void
		String body = """
				ld a0, 8(x2) # 0(SP) is the static link
				li a7, 4
				ecall
				""";
		wrappers.add(new Wrapper("puts", body, "a0", "a7"));

		// fun putint(x: int): void
		body = """
				ld a0, 8(x2)
				li a7, 1
				ecall
				""";
		wrappers.add(new Wrapper("putint", body, "a0", "a7"));

		// fun putchar(c: char): void
		body = """
				ld a0, 8(x2)
				# zero out everything but the last byte
				andi a0, a0, 0x0FF
				li a7, 11
				ecall
				""";
		wrappers.add(new Wrapper("putchar", body, "a0", "a7"));

		// fun getchar(): char
		body = """
				li a0, 0
				mv a1, x2
				   sd x0, 0(a1) # zero out buf
				li a2, 1
				li a7, 63
				ecall
				""";
		wrappers.add(new Wrapper("getchar", body, "a0", "a1", "a2", "a7", "t0"));

		// fun gets(buf: ^char, size: int): int
		body = """
				ld a1, 8(x2)        # buffer pointer
				ld a2, 16(x2)       # size
				add a2, a2, a2      # size * 2 because reasons?
				li a0, 0            # stdin file descriptor
				li a7, 63           # read syscall
				ecall

				# Null-terminate the string if we read something
				 beqz a0, gets_done  # if read returned 0, skip null termination
				ld t0, 8(x2)        # reload buffer pointer
				add t1, t0, a0      # calculate position after last read character

				# Check if last character is newline and replace with null
				addi t2, t1, -1     # position of last character
				lb t3, 0(t2)        # load last character
				li t4, 10           # newline character
				bne t3, t4, add_null
				sb zero, 0(t2)      # replace newline with null
				addi a0, a0, -1     # decrement count since we replaced newline
				j gets_done

				add_null:
				sb zero, 0(t1)      # add null terminator after last character

				gets_done:
				sd a0, 0(x2)        # store number of characters read (return value)
				""";
		wrappers.add(new Wrapper("gets", body, "a0", "a1", "a2", "a7", "t0", "t1", "t2", "t3", "t4"));

		// fun new(size: int): ^any
		body = """
				# Simple heap allocation using our custom heap pointer at 0x20000000
				li t0, 0x20000000
				ld t3, 0(t0)
				# Increase heap by requested size
				ld t1, 8(x2)        # load size argument
				add t3, t3, t1      # new break = current + size

				# Align the heap pointer to 8-byte boundary
				addi t3, t3, 7      # Add 7 to the address
				andi t3, t3, -8     # Mask off lower 3 bits (AND with ...11111000)

				sd t3, 0(t0)        # store new heap pointer
				# Check if allocation overlaps with the stack
				addi t2, x2, -8
				blt t2, t3, alloc_fail
				sd t3, 0(x2)        # store old heap pointer as result
				j alloc_done
				alloc_fail:
				li t0, 0            # return null on failure
				sd t0, 0(x2)
				alloc_done:
				nop
								""";
		Wrapper newWrapper = new Wrapper("new", body, "a0", "a7", "t0", "t1", "t2", "t3");
		newWrapper.alias = "malloc";
		wrappers.add(newWrapper);

		// fun getint(): int
		body = """
				mv t2, x0           # result = 0
				mv t3, x0           # sign flag (0=positive, 1=negative)

				maybe_read_empty:
				li a7, 63           # read syscall
				li a0, 0            # stdin
				mv a1, x2           # buffer is at stack pointer
				sd x0, 0(a1)        # zero out buffer
				li a2, 1            # read 1 character
				ecall

				blt a0, x0, EOF     # if read failed, go to EOF
				lb t0, 0(x2)        # load character

				parse_empty:
				li t1, 32           # space
				beq t0, t1, maybe_read_empty
				li t1, 13           # carriage return
				beq t0, t1, maybe_read_empty
				li t1, 10           # newline
				beq t0, t1, maybe_read_empty
				li t1, 9            # tab
				beq t0, t1, maybe_read_empty

				parse_sign:
				# Check for + or -
				addi t1, t0, -45    # check for '-'
				seqz t3, t1         # set sign flag if '-'
				beqz t1, read       # if '-', go read next char
				addi t1, t0, -43    # check for '+'
				bnez t1, parse_int  # if not '+', start parsing

				read:
				li a7, 63           # read syscall
				li a0, 0            # stdin
				mv a1, x2           # buffer
				sd x0, 0(a1)        # zero out buffer
				li a2, 1            # read 1 character
				ecall
				blt a0, x0, EOF     # if read failed, go to EOF
				lb t0, 0(x2)        # load character

				parse_int:
				slti t1, t0, 48     # if char < '0'
				bnez t1, EOF        # not a digit, finish
				slti t1, t0, 58     # if char <= '9'
				beqz t1, EOF        # not a digit, finish
				addi t0, t0, -48    # convert char to digit
				li t1, 10
				mul t2, t2, t1      # result *= 10
				add t2, t2, t0      # result += digit
				j read              # read next character

				EOF:
				beqz t3, STORE      # if positive, store result
				neg t2, t2          # if negative, negate result

				STORE:
				sd t2, 0(x2)        # store result
				""";
		wrappers.add(new Wrapper("getint", body, "a0", "a1", "a2", "a7", "t0", "t1", "t2", "t3"));

		// fun del(ptr: ^any): void (no-op for simplicity)
		body = """
				# Deallocation is a no-op in this simple implementation
				# In a real system, this would track allocated blocks
				""";
		Wrapper delWrapper = new Wrapper("del", body);
		delWrapper.alias = "free";
		wrappers.add(delWrapper);

		// fun at(ptr: ^any, idx: int, size_of_any: int): ^any
		body = """
				ld t0, 8(x2)        # ptr
				ld t1, 16(x2)       # idx
				ld t2, 24(x2)       # size

				# Calculate address: ptr + idx * size
				mul t1, t1, t2      # idx * size
				add t0, t0, t1      # ptr + (idx * size)

				sd t0, 0(x2)        # store result
				""";
		wrappers.add(new Wrapper("at_sys", body, "t0", "t1", "t2"));

		// fun printf(s: ^char, ...): void
		// This is complex, so we'll implement a simplified version that handles basic
		// formats
		body = """
				# Save current stack pointer for argument access
				mv t6, x2
				ld t0, 8(x2)        # format string pointer
				addi t5, x2, 16     # first vararg at offset 16

				printf_loop:
				lb t1, 0(t0)        # load current character
				beqz t1, printf_done

				li t2, 37           # '%'
				bne t1, t2, print_char

				# Handle format specifier
				addi t0, t0, 1      # skip '%'
				lb t1, 0(t0)        # load format character

				li t2, 100          # 'd' - integer
				beq t1, t2, print_int
				li t2, 99           # 'c' - character
				beq t1, t2, print_char_arg
				li t2, 115          # 's' - string
				beq t1, t2, print_string
				li t2, 37           # '%' - literal %
				beq t1, t2, print_char

				# Unknown format, just print the character
				j print_char

				print_int:
				ld a0, 0(t5)        # load integer argument
				addi t5, t5, 8      # advance to next argument
				li a7, 1            # print integer syscall
				ecall
				j next_char

				print_char_arg:
				ld a0, 0(t5)        # load character argument
				addi t5, t5, 8      # advance to next argument
				li a7, 11           # print character syscall
				ecall
				j next_char

				print_string:
				ld a0, 0(t5)        # load string pointer argument
				addi t5, t5, 8      # advance to next argument
				li a7, 4            # print string syscall
				ecall
				j next_char

				print_char:
				mv a0, t1           # character to print
				li a7, 11           # print character syscall
				ecall

				next_char:
				addi t0, t0, 1      # next character in format string
				j printf_loop

				printf_done:
				""";
		wrappers.add(new Wrapper("printf", body, "a0", "a7", "t0", "t1", "t2", "t5", "t6"));

		// fun random(lower, upper): int
		body = """
				# Simple linear congruential generator: next = (a * seed + c) mod m
				# Using constants: a=1664525, c=1013904223, m=2^32
				# use the address 0x80000000 as the seed
				li t0, 0x080000000
				ld t0, 0(t0)
				li t1, 1664525
				li t2, 1013904223
				mul t0, t0, t1
				add t0, t0, t2

				# Store updated seed back to memory
				li t4, 0x080000000
				sd t0, 0(t4)

				# Load arguments: always expect lower_bound and upper_bound
				ld t5, 8(x2)        # lower_bound
				ld t3, 16(x2)       # upper_bound

				# Calculate range and apply bounds
				sub t3, t3, t5      # t3 = range = upper_bound - lower_bound
				remu t0, t0, t3     # t0 = random % range
				add t0, t0, t5      # t0 = (random % range) + lower_bound

				sd t0, 0(x2)        # store result
				""";
		wrappers.add(new Wrapper("random", body, "t0", "t1", "t2", "t3", "t4", "t5", "t6"));

		// fun seed(seed: int): void
		body = """
				# Store seed in a known memory location for random() to use
				# use the address 0x80000000 as the seed
				ld a0, 8(x2)
				li t0, 0x080000000
				sd a0, 0(t0)
				""";
		wrappers.add(new Wrapper("seed", body, "a0", "t0"));

		return wrappers;
	}

}
