/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.program.model.lang;

import static org.junit.Assert.assertEquals;

import org.junit.*;

import ghidra.app.util.PseudoDisassembler;
import ghidra.app.util.PseudoInstruction;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.test.AbstractGhidraHeadlessIntegrationTest;

/**
 * Instruction-decode coverage for the NEC V-Series (V50 variant): the 8086
 * base, the 80186 additions, and the NEC-exclusive instructions, all asserted
 * with the NEC mnemonics the module emits.  Expected strings were captured from
 * the module's own disassembler; every instruction is decoded at a fixed
 * address so relative-branch targets are deterministic.
 */
public class VSeriesDisassemblyTest extends AbstractGhidraHeadlessIntegrationTest {

	private static final String ADDR = "b000:0000"; // phys 0xB0000

	private ProgramBuilder programBuilder;
	private Program program;
	private PseudoDisassembler disassembler;
	private int txId;

	@Before
	public void setUp() throws Exception {
		programBuilder = new ProgramBuilder("nec_vseries", "V-Series:LE:16:V50");
		program = programBuilder.getProgram();
		txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", ADDR, 0x1000).setExecute(true);
		disassembler = new PseudoDisassembler(program);
	}

	@After
	public void tearDown() throws Exception {
		if (program != null) {
			program.endTransaction(txId, true);
		}
		if (programBuilder != null) {
			programBuilder.dispose();
		}
	}

	private void assertDisassembly(String bytes, String expected) throws Exception {
		programBuilder.setBytes(ADDR, bytes);
		Address a = program.getAddressFactory().getAddress(ADDR);
		PseudoInstruction instr = disassembler.disassemble(a);
		assertEquals("bytes " + bytes, expected, instr.toString());
	}

	@Test
	public void test_dataTransfer() throws Exception {
		assertDisassembly("b012", "MOV AL,0x12");
		assertDisassembly("b83412", "MOV AW,0x1234");
		assertDisassembly("88c3", "MOV BL,AL");
		assertDisassembly("89c3", "MOV BW,AW");
		assertDisassembly("8ac3", "MOV AL,BL");
		assertDisassembly("8bc3", "MOV AW,BW");
		assertDisassembly("c60712", "MOV byte ptr [BW],0x12");
		assertDisassembly("c7073412", "MOV word ptr [BW],0x1234");
		assertDisassembly("8ed8", "MOV DS0,AW");
		assertDisassembly("8cd8", "MOV AW,DS0");
		assertDisassembly("a03412", "MOV AL,[ 0x1234 ]");
		assertDisassembly("a23412", "MOV [ 0x1234 ],AL");
		assertDisassembly("86c3", "XCH BL,AL");
		assertDisassembly("87c3", "XCH BW,AW");
		assertDisassembly("91", "XCH AW,CW");
		assertDisassembly("8d07", "LDEA AW,[BW]");
		assertDisassembly("d7", "TRANS");
		assertDisassembly("c507", "MOV DS0,AW,[BW]");
		assertDisassembly("c407", "MOV DS1,AW,[BW]");
		assertDisassembly("9f", "MOV AH,PSW");
		assertDisassembly("9e", "MOV PSW,AH");
		assertDisassembly("268b07", "MOV AW,word ptr DS1:[BW]");
	}

	@Test
	public void test_stack() throws Exception {
		assertDisassembly("50", "PUSH AW");
		assertDisassembly("58", "POP AW");
		assertDisassembly("06", "PUSH DS1");
		assertDisassembly("07", "POP DS1");
		assertDisassembly("683412", "PUSH 0x1234");
		assertDisassembly("6a05", "PUSH 0x5");
		assertDisassembly("9c", "PUSH PSW");
		assertDisassembly("9d", "POP PSW");
	}

	@Test
	public void test_arithmeticLogic() throws Exception {
		assertDisassembly("00c3", "ADD BL,AL");
		assertDisassembly("03c3", "ADD AW,BW");
		assertDisassembly("0412", "ADD AL,0x12");
		assertDisassembly("053412", "ADD AW,0x1234");
		assertDisassembly("10c3", "ADDC BL,AL");
		assertDisassembly("28c3", "SUB BL,AL");
		assertDisassembly("18c3", "SUBC BL,AL");
		assertDisassembly("38c3", "CMP BL,AL");
		assertDisassembly("80c305", "ADD BL,0x5");
		assertDisassembly("83c005", "ADD AW,0x5");
		assertDisassembly("40", "INC AW");
		assertDisassembly("48", "DEC AW");
		assertDisassembly("fec0", "INC AL");
		assertDisassembly("f6d8", "NEG AL");
		assertDisassembly("f6d0", "NOT AL");
		assertDisassembly("f6e3", "MULU BL");
		assertDisassembly("f6eb", "MUL BL");
		assertDisassembly("f6f3", "DIVU BL");
		assertDisassembly("f6fb", "DIV BL");
		assertDisassembly("84c3", "TEST BL,AL");
		assertDisassembly("20c3", "AND BL,AL");
		assertDisassembly("08c3", "OR BL,AL");
		assertDisassembly("30c3", "XOR BL,AL");
		assertDisassembly("a812", "TEST AL,0x12");
		assertDisassembly("f00007", "ADD byte ptr [BW],AL");
	}

	@Test
	public void test_convertAndBcdAdjust() throws Exception {
		assertDisassembly("98", "CVTBW");
		assertDisassembly("99", "CVTWL");
		assertDisassembly("d40a", "CVTBD 0xa");
		assertDisassembly("d50a", "CVTDB 0xa");
		assertDisassembly("37", "ADJBA");
		assertDisassembly("27", "ADJ4A");
		assertDisassembly("3f", "ADJBS");
		assertDisassembly("2f", "ADJ4S");
	}

	@Test
	public void test_shiftRotate() throws Exception {
		assertDisassembly("d0e0", "SHL AL,1");
		assertDisassembly("d2e0", "SHL AL,CL");
		assertDisassembly("d0e8", "SHR AL,1");
		assertDisassembly("d0f8", "SHRA AL,1");
		assertDisassembly("d0c0", "ROL AL,1");
		assertDisassembly("d0c8", "ROR AL,1");
		assertDisassembly("d0d0", "ROLC AL,1");
		assertDisassembly("d0d8", "RORC AL,1");
		assertDisassembly("c0e003", "SHL AL,0x3");
		assertDisassembly("c1e003", "SHL AW,0x3");
	}

	@Test
	public void test_stringBlock() throws Exception {
		assertDisassembly("a4", "MOVBK DS1:[IY],[IX]");
		assertDisassembly("a5", "MOVBK DS1:[IY],[IX]");
		assertDisassembly("a6", "CMPBK [IX],DS1:[IY]");
		assertDisassembly("ae", "CMPM DS1:[IY]");
		assertDisassembly("ac", "LDM [IX]");
		assertDisassembly("aa", "STM DS1:[IY]");
		assertDisassembly("64a6", "CMPBK [IX],DS1:[IY]");
		assertDisassembly("65a7", "CMPBK [IX],DS1:[IY]");
	}

	@Test
	public void test_stringRepeatPrefix() throws Exception {
		assertDisassembly("f3a4", "MOVBK.rep DS1:[IY],[IX]");
		assertDisassembly("f2a6", "CMPBK.repne [IX],DS1:[IY]");
	}

	@Test
	public void test_portIo() throws Exception {
		assertDisassembly("e446", "IN AL,0x46");
		assertDisassembly("e646", "OUT 0x46,AL");
		assertDisassembly("ec", "IN AL,DW");
		assertDisassembly("ed", "IN AW,DW");
		assertDisassembly("ee", "OUT DW,AL");
		assertDisassembly("ef", "OUT DW,AW");
		assertDisassembly("6c", "INM DS1:[IY],DW");
		assertDisassembly("6d", "INM DS1:[IY],DW");
		assertDisassembly("6e", "OUTM DW,[IX]");
		assertDisassembly("6f", "OUTM DW,[IX]");
	}

	@Test
	public void test_controlTransfer() throws Exception {
		assertDisassembly("eb10", "BR 0xb000:0012");
		assertDisassembly("e90001", "BR 0xb000:0103");
		assertDisassembly("ea000000b0", "BR 0xb000:0000");
		assertDisassembly("ffe3", "BR BW");
		assertDisassembly("e80001", "CALL 0xb000:0103");
		assertDisassembly("9a000000b0", "CALL 0xb000:0000");
		assertDisassembly("ffd3", "CALL BW");
		assertDisassembly("c3", "RET");
		assertDisassembly("c20400", "RET 0x4");
		assertDisassembly("cb", "RET");
		assertDisassembly("ca0400", "RET 0x4");
	}

	@Test
	public void test_conditionalAndLoop() throws Exception {
		assertDisassembly("7002", "BV 0xb000:0004");
		assertDisassembly("7102", "BNV 0xb000:0004");
		assertDisassembly("7202", "BC 0xb000:0004");
		assertDisassembly("7302", "BNC 0xb000:0004");
		assertDisassembly("7402", "BE 0xb000:0004");
		assertDisassembly("7502", "BNE 0xb000:0004");
		assertDisassembly("7602", "BNH 0xb000:0004");
		assertDisassembly("7702", "BH 0xb000:0004");
		assertDisassembly("7802", "BN 0xb000:0004");
		assertDisassembly("7902", "BP 0xb000:0004");
		assertDisassembly("7a02", "BPE 0xb000:0004");
		assertDisassembly("7b02", "BPO 0xb000:0004");
		assertDisassembly("7c02", "BLT 0xb000:0004");
		assertDisassembly("7d02", "BGE 0xb000:0004");
		assertDisassembly("7e02", "BLE 0xb000:0004");
		assertDisassembly("7f02", "BGT 0xb000:0004");
		assertDisassembly("e002", "DBNZNE 0xb000:0004");
		assertDisassembly("e102", "DBNZE 0xb000:0004");
		assertDisassembly("e202", "DBNZ 0xb000:0004");
		assertDisassembly("e302", "BCWZ 0xb000:0004");
	}

	@Test
	public void test_interrupt() throws Exception {
		assertDisassembly("cc", "BRK 3");
		assertDisassembly("cd21", "BRK 0x21");
		assertDisassembly("ce", "BRKV");
		assertDisassembly("cf", "RETI");
	}

	@Test
	public void test_processorControl() throws Exception {
		assertDisassembly("90", "NOP");
		assertDisassembly("f4", "HALT");
		assertDisassembly("9b", "POLL");
		assertDisassembly("fb", "EI");
		assertDisassembly("fa", "DI");
		assertDisassembly("6207", "CHKIND AW,[BW]");
	}

	@Test
	public void test_flagOps() throws Exception {
		assertDisassembly("f8", "CLR1 CY");
		assertDisassembly("f9", "SET1 CY");
		assertDisassembly("f5", "NOT1 CY");
		assertDisassembly("fc", "CLR1 DIR");
		assertDisassembly("fd", "SET1 DIR");
	}

	@Test
	public void test_necBitManip() throws Exception {
		assertDisassembly("0f10c1", "TEST1 CL,CL");
		assertDisassembly("0f12c0", "CLR1 AL,CL");
		assertDisassembly("0f14c3", "SET1 BL,CL");
		assertDisassembly("0f16c2", "NOT1 DL,CL");
		assertDisassembly("0f18c005", "TEST1 AL,0x5");
		assertDisassembly("0f1ac005", "CLR1 AL,0x5");
		assertDisassembly("0f1cc005", "SET1 AL,0x5");
		assertDisassembly("0f1ec005", "NOT1 AL,0x5");
	}

	@Test
	public void test_necBcdString() throws Exception {
		assertDisassembly("0f20", "ADD4S");
		assertDisassembly("0f22", "SUB4S");
		assertDisassembly("0f26", "CMP4S");
		assertDisassembly("0f28c0", "ROL4 AL");
		assertDisassembly("0f2ac3", "ROR4 BL");
	}

	@Test
	public void test_necBitField() throws Exception {
		assertDisassembly("0f31c1", "INS AL,CL");
		assertDisassembly("0f33c1", "EXT AL,CL");
		assertDisassembly("0f39c105", "INS CL,0x5");
		assertDisassembly("0f3bc105", "EXT CL,0x5");
	}

	@Test
	public void test_mode8080() throws Exception {
		assertDisassembly("0fff05", "BRKEM 0x5");
		assertDisassembly("0ffd", "RETEM");
		assertDisassembly("0fed07", "CALLN 0x7");
	}
}
