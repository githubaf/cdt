/*
 *(c) Copyright QNX Software Systems Ltd. 2002.
 * All Rights Reserved.
 * 
 */
package org.eclipse.cdt.debug.mi.core.cdi;

import java.util.StringTokenizer;

import org.eclipse.cdt.debug.core.cdi.CDIException;
import org.eclipse.cdt.debug.core.cdi.ICDISourceManager;
import org.eclipse.cdt.debug.core.cdi.model.ICDIInstruction;
import org.eclipse.cdt.debug.core.cdi.model.ICDIMixedInstruction;
import org.eclipse.cdt.debug.mi.core.MIException;
import org.eclipse.cdt.debug.mi.core.MISession;
import org.eclipse.cdt.debug.mi.core.cdi.model.Instruction;
import org.eclipse.cdt.debug.mi.core.cdi.model.MixedInstruction;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.ArrayType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.BoolType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.CharType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.DoubleType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.EnumType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.FloatType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.FunctionType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.IntType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.LongLongType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.LongType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.PointerType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.ReferenceType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.ShortType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.StructType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.Type;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.VoidType;
import org.eclipse.cdt.debug.mi.core.cdi.model.type.WCharType;
import org.eclipse.cdt.debug.mi.core.command.CommandFactory;
import org.eclipse.cdt.debug.mi.core.command.MIDataDisassemble;
import org.eclipse.cdt.debug.mi.core.command.MIEnvironmentDirectory;
import org.eclipse.cdt.debug.mi.core.command.MIGDBShowDirectories;
import org.eclipse.cdt.debug.mi.core.command.MIPType;
import org.eclipse.cdt.debug.mi.core.output.MIAsm;
import org.eclipse.cdt.debug.mi.core.output.MIDataDisassembleInfo;
import org.eclipse.cdt.debug.mi.core.output.MIGDBShowDirectoriesInfo;
import org.eclipse.cdt.debug.mi.core.output.MIPTypeInfo;
import org.eclipse.cdt.debug.mi.core.output.MISrcAsm;


/**
 */
public class SourceManager extends SessionObject implements ICDISourceManager {

	boolean autoupdate;

	public SourceManager(Session session) {
		super(session);
		autoupdate = false;
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#addSourcePaths(String[])
	 */
	public void addSourcePaths(String[] dirs) throws CDIException {
		Session session = (Session)getSession();
		MISession mi = session.getMISession();
		CommandFactory factory = mi.getCommandFactory();
		MIEnvironmentDirectory dir = factory.createMIEnvironmentDirectory(dirs);
		try {
			mi.postCommand(dir);
			dir.getMIInfo();
		} catch (MIException e) {
			throw new MI2CDIException(e);
		}
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#getSourcePaths()
	 */
	public String[] getSourcePaths() throws CDIException {
		Session session = (Session)getSession();
		MISession mi = session.getMISession();
		CommandFactory factory = mi.getCommandFactory();
		MIGDBShowDirectories dir = factory.createMIGDBShowDirectories();
		try {
			mi.postCommand(dir);
			MIGDBShowDirectoriesInfo info = dir.getMIGDBShowDirectoriesInfo();
			return info.getDirectories();
		} catch (MIException e) {
			throw new MI2CDIException(e);
		}
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#getInstructions(String, int, int)
	 */
	public ICDIInstruction[] getInstructions(String filename, int linenum, int lines) throws CDIException {
		Session session = (Session)getSession();
		MISession mi = session.getMISession();
		CommandFactory factory = mi.getCommandFactory();
		MIDataDisassemble dis = factory.createMIDataDisassemble(filename, linenum, lines, false);
		try {
			mi.postCommand(dis);
			MIDataDisassembleInfo info = dis.getMIDataDisassembleInfo();
			MIAsm[] asm = info.getMIAsms();
			Instruction[] instructions = new Instruction[asm.length];
			for (int i = 0; i < instructions.length; i++) {
				instructions[i] = new Instruction(session.getCurrentTarget(), asm[i]);
			}
			return instructions;
		} catch (MIException e) {
			throw new MI2CDIException(e);
		}
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#getInstructions(String, int)
	 */
	public ICDIInstruction[] getInstructions(String filename, int linenum) throws CDIException {
		return getInstructions(filename, linenum, -1);
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#getInstructions(long, long)
	 */
	public ICDIInstruction[] getInstructions(long start, long end) throws CDIException {
		Session session = (Session)getSession();
		MISession mi = session.getMISession();
		CommandFactory factory = mi.getCommandFactory();
		String hex = "0x";
		String sa = hex + Long.toHexString(start);
		String ea = hex + Long.toHexString(end);
		MIDataDisassemble dis = factory.createMIDataDisassemble(sa, ea, false);
		try {
			mi.postCommand(dis);
			MIDataDisassembleInfo info = dis.getMIDataDisassembleInfo();
			MIAsm[] asm = info.getMIAsms();
			Instruction[] instructions = new Instruction[asm.length];
			for (int i = 0; i < instructions.length; i++) {
				instructions[i] = new Instruction(session.getCurrentTarget(), asm[i]);
			}
			return instructions;
		} catch (MIException e) {
			throw new MI2CDIException(e);
		}
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#getMixedInstructions(String, int, int)
	 */
	public ICDIMixedInstruction[] getMixedInstructions(String filename, int linenum, int lines) throws CDIException {
		Session session = (Session)getSession();
		MISession mi = session.getMISession();
		CommandFactory factory = mi.getCommandFactory();
		MIDataDisassemble dis = factory.createMIDataDisassemble(filename, linenum, lines, true);
		try {
			mi.postCommand(dis);
			MIDataDisassembleInfo info = dis.getMIDataDisassembleInfo();
			MISrcAsm[] srcAsm = info.getMISrcAsms();
			ICDIMixedInstruction[] mixed = new ICDIMixedInstruction[srcAsm.length];
			for (int i = 0; i < mixed.length; i++) {
				mixed[i] = new MixedInstruction(session.getCurrentTarget(), srcAsm[i]);
			}
			return mixed;
		} catch (MIException e) {
			throw new MI2CDIException(e);
		}
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#getMixedInstructions(String, int)
	 */
	public ICDIMixedInstruction[] getMixedInstructions(String filename, int linenum) throws CDIException {
		return getMixedInstructions(filename, linenum, -1);
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#getMixedInstructions(long, long)
	 */
	public ICDIMixedInstruction[] getMixedInstructions(long start, long end) throws CDIException {
		Session session = (Session)getSession();
		MISession mi = session.getMISession();
		CommandFactory factory = mi.getCommandFactory();
		String hex = "0x";
		String sa = hex + Long.toHexString(start);
		String ea = hex + Long.toHexString(end);
		MIDataDisassemble dis = factory.createMIDataDisassemble(sa, ea, true);
		try {
			mi.postCommand(dis);
			MIDataDisassembleInfo info = dis.getMIDataDisassembleInfo();
			MISrcAsm[] srcAsm = info.getMISrcAsms();
			ICDIMixedInstruction[] mixed = new ICDIMixedInstruction[srcAsm.length];
			for (int i = 0; i < mixed.length; i++) {
				mixed[i] = new MixedInstruction(session.getCurrentTarget(), srcAsm[i]);
			}
			return mixed;
		} catch (MIException e) {
			throw new MI2CDIException(e);
		}
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#isAutoUpdate()
	 */
	public boolean isAutoUpdate() {
		return autoupdate;
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#setAutoUpdate(boolean)
	 */
	public void setAutoUpdate(boolean update) {
		autoupdate = update;
	}

	/**
	 * @see org.eclipse.cdt.debug.core.cdi.ICDISourceManager#update()
	 */
	public void update() throws CDIException {
	}


	public Type getType(String name) throws CDIException {
		String typename = name.trim();

		// Check the derived types and agregate types
		if (typename.endsWith("]")) {
			return new ArrayType(typename);
		} else if (typename.indexOf("*") != -1) {
			return new PointerType(typename);
		} else if (typename.indexOf("&") != -1) {
			return new ReferenceType(typename);
		} else if (typename.endsWith(")")) {
			return new FunctionType(typename);
		} else if (typename.startsWith("enum ")) {
			return new EnumType(typename);
		} else if (typename.startsWith("union ")) {
			return new StructType(typename);
		} else if (typename.startsWith("struct ")) {
			return new StructType(typename);
		} else if (typename.startsWith("class ")) {
			return new StructType(typename);
		}

		// Check the primitives.
		if (typename.equals("char")) {
			return new CharType(typename);
		} else if (typename.equals("wchar_t")) {
			return new WCharType(typename);
		} else if (typename.equals("short")) {
			return new ShortType(typename);
		} else if (typename.equals("int")) {
			return new IntType(typename);
		} else if (typename.equals("long")) {
			return new LongType(typename);
		} else if (typename.equals("unsigned")) {
			return new IntType(typename, true);
		} else if (typename.equals("signed")) {
			return new IntType(typename);
		} else if (typename.equals("bool")) {
			return new BoolType(typename);
		} else if (typename.equals("_Bool")) {
			return new BoolType(typename);
		} else if (typename.equals("float")) {
			return new FloatType(typename);
		} else if (typename.equals("double")) {
			return new DoubleType(typename);
		} else if (typename.equals("void")) {
			return new VoidType(typename);
		}

		StringTokenizer st = new StringTokenizer(typename);
		int count = st.countTokens();

		if (count == 2) {
			String first = st.nextToken();
			String second = st.nextToken();

			// ISOC allows permutations:
			// "signed int" and "int signed" are equivalent
			boolean isUnsigned =  (first.equals("unsigned") || second.equals("unsigned"));
			boolean isSigned =    (first.equals("signed") || second.equals("signed"));
			boolean isChar =      (first.equals("char") || second.equals("char"));
			boolean isInt =       (first.equals("int") || second.equals("int"));
			boolean isLong =      (first.equals("long") || second.equals("long"));
			boolean isShort =     (first.equals("short") || second.equals("short"));
			boolean isLongLong =  (first.equals("long") && second.equals("long"));
			boolean isDouble =    (first.equals("double") || second.equals("double"));
			boolean isFloat =     (first.equals("float") || second.equals("float"));
			boolean isComplex =   (first.equals("complex") || second.equals("complex") ||
			                       first.equals("_Complex") || second.equals("_Complex"));
			boolean isImaginery = (first.equals("_Imaginary") || second.equals("_Imaginary"));

			if (isChar && (isSigned || isUnsigned)) {
				return new CharType(typename, isUnsigned);
			} else if (isShort && (isSigned || isUnsigned)) {
				return new ShortType(typename, isUnsigned);
			} else if (isInt && (isSigned || isUnsigned)) {
				return new IntType(typename, isUnsigned);
			} else if (isLong && (isInt || isSigned || isUnsigned)) {
				return new LongType(typename, isUnsigned);
			} else if (isLongLong) {
				return new LongLongType(typename);
			} else if (isDouble && (isLong || isComplex || isImaginery)) {
				return new DoubleType(typename, isComplex, isImaginery, isLong);
			} else if (isFloat && (isComplex || isImaginery)) {
				return new FloatType(typename, isComplex, isImaginery);
			}
		} else if (count == 3) {
			// ISOC allows permutation. replace short by: long or short
			// "unsigned short int", "unsigned int short"
			// "short unsigned int". "short int unsigned"
			// "int unsinged short". "int short unsigned"
			//
			// "unsigned long long", "long long unsigned"
			// "signed long long", "long long signed"
			String first = st.nextToken();
			String second = st.nextToken();
			String third = st.nextToken();

			boolean isSigned =    (first.equals("signed") || second.equals("signed") || third.equals("signed"));
			boolean unSigned =    (first.equals("unsigned") || second.equals("unsigned") || third.equals("unsigned"));
			boolean isInt =       (first.equals("int") || second.equals("int") || third.equals("int"));
			boolean isLong =      (first.equals("long") || second.equals("long") || third.equals("long"));
			boolean isShort =     (first.equals("short") || second.equals("short") || third.equals("short"));
			boolean isLongLong =  (first.equals("long") && second.equals("long")) ||
			                       (second.equals("long") && third.equals("long"));
			boolean isDouble =    (first.equals("double") || second.equals("double") || third.equals("double"));
			boolean isComplex =   (first.equals("complex") || second.equals("complex") || third.equals("complex") ||
			                       first.equals("_Complex") || second.equals("_Complex") || third.equals("_Complex"));
			boolean isImaginery = (first.equals("_Imaginary") || second.equals("_Imaginary") || third.equals("_Imaginary"));


			if (isShort && isInt && (isSigned || unSigned)) {
				return new ShortType(typename, unSigned);
			} else if (isLong && isInt && (isSigned || unSigned)) {
				return new LongType(typename, unSigned);
			} else if (isLongLong && (isSigned || unSigned)) {
				return new LongLongType(typename, unSigned);
			} else if (isDouble && isLong && (isComplex || isImaginery)) {
				return new DoubleType(typename, isComplex, isImaginery, isLong);
			}
		} else if (count == 4) {
			// ISOC allows permutation:
			// "unsigned long long int", "unsigned int long long"
			// "long long unsigned int". "long long int unsigned"
			// "int unsigned long long". "int long long unsigned"
			String first = st.nextToken();
			String second = st.nextToken();
			String third = st.nextToken();
			String fourth = st.nextToken();

			boolean unSigned = (first.equals("unsigned") || second.equals("unsigned") || third.equals("unsigned") || fourth.equals("unsigned"));
			boolean isSigned = (first.equals("signed") || second.equals("signed") || third.equals("signed") || fourth.equals("signed"));
			boolean isInt =    (first.equals("int") || second.equals("int") || third.equals("int") || fourth.equals("int"));
			boolean isLongLong =   (first.equals("long") && second.equals("long"))
				|| (second.equals("long") && third.equals("long"))
				|| (third.equals("long") && fourth.equals("long"));

			if (isLongLong && isInt && (isSigned || unSigned)) {
				return new LongLongType(typename, unSigned);
			}
		}
		throw new CDIException("Unknown type");
	}


	public String getDetailTypeName(String typename) throws CDIException {
		try {
			Session session = (Session)getSession();
			MISession mi = session.getMISession();
			CommandFactory factory = mi.getCommandFactory();
			MIPType ptype = factory.createMIPType(typename);
			mi.postCommand(ptype);
			MIPTypeInfo info = ptype.getMIPtypeInfo();
			if (info == null) {
				throw new CDIException("No answer");
			}
			return info.getType();
		} catch (MIException e) {
			throw new MI2CDIException(e);
		}
	}

}
