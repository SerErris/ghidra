# NEC V-Series

Ghidra processor module for the **NEC V-Series** 16-bit microprocessors
(V20 / µPD70108, V30 / µPD70116, V40 / µPD70208, V50 / µPD70216), **16-bit
real mode only**.

The V-Series cores are 8086/8088-compatible with the 80186 instruction
additions and a set of NEC-exclusive instructions (bit manipulation,
packed-BCD string arithmetic, bit-field insert/extract, and an 8080 emulation
mode). All four parts decode the **same** instruction set, so a single SLEIGH
specification (`vseries.sla`) is shared by every variant; the parts differ only
in their `.pspec`/`.ldefs`.

This module is self-contained and does not depend on, or modify, the x86
module. The V-Series redefines the `0F` byte as its own instruction prefix
(x86 uses it as the two-byte-opcode escape) and redefines `64`/`65` (x86 FS/GS
override) as the `REPNC`/`REPC` prefixes, so a lean dedicated 8086/80186
decoder is both cleaner and more correct for 16-bit code than an x86 variant.

## Variants

| Language ID | Part | Core | Ext. data bus | On-chip peripherals |
|---|---|---|---|---|
| `V-Series:LE:16:V20` | µPD70108 | 8088-compatible, enhanced | 8-bit | none (CPU only) |
| `V-Series:LE:16:V30` | µPD70116 | 8086-compatible, enhanced | 16-bit | none (CPU only) |
| `V-Series:LE:16:V40` | µPD70208 | V20-compatible + peripherals | 8-bit | DMA, timer, ICU, serial |
| `V-Series:LE:16:V50` | µPD70216 | V30-compatible + peripherals | 16-bit | DMA, timer, ICU, serial |

The external data-bus width is a hardware/timing detail invisible to the
disassembler, so it does not affect decoding. Only the V40/V50 add the on-chip
peripheral SFRs (see below).

## Register model

The standard 8086 programmer model. **NEC data-book register, flag and mnemonic
names are used throughout**; the Intel 8086 equivalents are given in comments
and here for cross-reference:

| NEC | Intel | | NEC | Intel |
|---|---|---|---|---|
| AW BW CW DW | AX BX CX DX | | PS (program segment) | CS |
| SP BP IX IY | SP BP SI DI | | DS0 (data segment 0) | DS |
| PC (program counter) | IP | | DS1 (data segment 1) | ES |
| PSW | FLAGS | | SS (stack segment) | SS |

The 8-bit halves keep their shared names `AL/AH BL/BH CL/CH DL/DH`. The flag
bits use the NEC names `CY P AC Z S BRK IE DIR V` (Intel `CF PF AF ZF SF TF IF
DF OF`).

There are **no** extra programmer-visible registers: the V-Series
"enhancements" are micro-architectural plus extra instructions, not new
registers. There is **no** FS/GS and there are no 32-bit registers.

## Addressing and segmentation

16-bit real mode. A physical address is `(segment << 4) + 16-bit offset`, a
20-bit (1 MB) address formed by the bus interface unit. Ghidra disassembles in
a flat 20-bit linear space (4-byte `ram` addresses), so:

- The program counter `PC` (Intel `IP`) is declared 4 bytes wide so it can hold
  the linear instruction address Ghidra tracks; its low 16 bits are the
  architectural 16-bit `PC`. The extra width is a Ghidra modeling requirement —
  the silicon has only the 16-bit `PC` (Intel models the same thing as
  `EIP`/`IP`).
- Near relative branches wrap the offset within the 64 K segment; the physical
  address wraps at 1 MB (20-bit address bus). Both are modeled. Exact wrap for
  non-64K-aligned code segments is approximate (the runtime segment base is not
  available at decode time — the same limitation as `x86-16-real`).

## Instruction layers

- `vseries_8086.sinc` — the full 8086 base instruction set + p-code.
- `vseries_80186.sinc` — the 80186 additions, NEC mnemonics primary
  (`PREPARE`=ENTER, `DISPOSE`=LEAVE, `PUSH R`=PUSHA, `POP R`=POPA, 3-operand
  `MUL`=IMUL, `CHKIND`=BOUND, `INM`=INS, `OUTM`=OUTS, shifts/rotates by imm8).
- `vseries_nec.sinc` — the NEC-exclusive instructions (common to all four
  parts): bit ops `TEST1`/`CLR1`/`SET1`/`NOT1` (bit from CL or imm8), packed-BCD
  string `ADD4S`/`SUB4S`/`CMP4S`, nibble rotate `ROL4`/`ROR4`, bit-field
  `INS`/`EXT`, the `REPC`/`REPNC` prefixes, and the 8080-mode switches.

NEC mnemonics are primary throughout (e.g. `BR`=JMP, `Bcc`=Jcc, `MOVBK`=MOVS,
`ADDC`=ADC, `SHRA`=SAR); the Intel alias is given in a comment.

## I/O and on-chip peripheral SFRs (V40/V50)

`IN`/`OUT`/`INM`/`OUTM` access a separate 64 K `io` space. On the V40/V50 the
fixed "system I/O area" control registers (I/O addresses `FF00H`–`FFFFH`) are
provided as named symbols via the per-variant `.pspec` `default_symbols`:
`OPHA`, `OPSEL`, `OPCN`, `DULA`, `IULA`, `TULA`, `SULA`, `WCY1`, `WCY2`, `WMB`,
`RFC`, `TCKS`. When Ghidra resolves the port value (e.g. `MOV DW,0xFFFC; OUT
DW,AL`) the access is shown against the register name (`OPHA`).

The relocatable DMAU/ICU/TCU/SCU peripheral register blocks are based at the
firmware-programmed `OPHA` address and so are not given fixed symbols.
V20/V30 have no on-chip peripherals.

## 8080 emulation mode

The V-Series can enter an 8080 emulation mode via `BRKEM`. This module decodes
the mode-switch instructions `BRKEM`, `RETEM` and `CALLN` (as named p-code
operations) but does **not** decode 8080 opcodes inside emulation mode; that is
out of scope. Code between a `BRKEM` and its `RETEM` is 8080 machine code and
should be treated accordingly.

## Files

```
data/languages/
  vseries.slaspec            compiled -> vseries.sla (shared by all variants)
  vseries_registers.sinc     spaces, 8086 register model, io space, context
  vseries_tokens.sinc        opcode/modrm/imm tokens, 16-bit addressing modes
  vseries_8086.sinc          8086 base instructions + p-code
  vseries_80186.sinc         80186 additions (NEC mnemonics)
  vseries_nec.sinc           NEC-exclusive instructions
  V-Series.ldefs             the four language variants
  V20.pspec V30.pspec        CPU-only processor specs
  V40.pspec V50.pspec        processor specs incl. on-chip peripheral SFRs
  V-Series.cspec             16-bit segmented calling conventions
data/manuals/
  V-Series.idx               manual index
```

## Building and testing

Compile the SLEIGH spec with the Ghidra sleigh compiler (gradle task, or
`support/sleigh data/languages/vseries.slaspec`). The spec compiles with zero
warnings.

Functional verification (via `analyzeHeadless` on the reference firmware, a
Korg 01/W 512 KB image based at `0x80000`):

- reset vector `0xFFFF0` = `EA 00 00 00 B0` decodes as `BR B000:0000` (far jump
  to physical `0xB0000`);
- the boot code at `0xB0000` disassembles cleanly (RAM test with `5555`/`AAAA`
  patterns, `DI`, `MOV DS0`, port init);
- the 80186 and NEC-exclusive instructions decode with the NEC mnemonics;
- offset/segment wrap and 20-bit physical wrap are exercised with crafted cases;
- all four variants load, and the V40/V50 SFR symbols resolve on `IN`/`OUT`.

## References

- 1991 NEC 16-Bit V-Series Microprocessor Data Book
- NEC µPD70216 (V50) data sheet — register model, I/O area / OPHA map, 8080 mode
- 8086/80186 instruction-set references
