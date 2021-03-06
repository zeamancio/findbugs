/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2004-2006 University of Maryland
 * Copyright (C) 2008 Google
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.detect;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.classfile.Code;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.IntAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.StringAnnotation;
import edu.umd.cs.findbugs.SystemProperties;
import edu.umd.cs.findbugs.TypeAnnotation;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;
import edu.umd.cs.findbugs.formatStringChecker.Formatter;
import edu.umd.cs.findbugs.formatStringChecker.ExtraFormatArgumentsException;
import edu.umd.cs.findbugs.formatStringChecker.IllegalFormatConversionException;
import edu.umd.cs.findbugs.formatStringChecker.MissingFormatArgumentException;

public class FormatStringChecker extends OpcodeStackDetector {
	
	final BugReporter bugReporter;

	public FormatStringChecker(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	enum FormatState {
		NONE, READY_FOR_FORMAT, EXPECTING_ASSIGNMENT
	};

	FormatState state;

	String formatString;

	int stackDepth;

	OpcodeStack.Item arguments[];

	@Override
	public void visit(Code code) {
		state = FormatState.NONE;
		super.visit(code); // make callbacks to sawOpcode for all opcodes
		arguments = null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.umd.cs.findbugs.bcel.OpcodeStackDetector#sawOpcode(int)
	 */
	@Override
	public void sawOpcode(int seen) {
		// System.out.println(getPC() + " " + OPCODE_NAMES[seen] + " " + state);

		if (stack.getStackDepth() < stackDepth) {
			state = FormatState.NONE;
			stackDepth = 0;
			arguments = null;
		}
		if (seen == ANEWARRAY && stack.getStackDepth() >= 2) {
			Object size = stack.getStackItem(0).getConstant();
			Object formatStr = stack.getStackItem(1).getConstant();
			if (size instanceof Integer && formatStr instanceof String) {
				arguments = new OpcodeStack.Item[(Integer) size];
				this.formatString = (String) formatStr;
				state = FormatState.READY_FOR_FORMAT;
				stackDepth = stack.getStackDepth();
			}
		} else if (state == FormatState.READY_FOR_FORMAT && seen == DUP) {
	        state = FormatState.EXPECTING_ASSIGNMENT;
        } else if (state == FormatState.EXPECTING_ASSIGNMENT && stack.getStackDepth() == stackDepth + 3 && seen == AASTORE) {
			Object pos = stack.getStackItem(1).getConstant();
			OpcodeStack.Item value = stack.getStackItem(0);
			if (pos instanceof Integer) {
				int index = (Integer) pos;
				if (index >= 0 && index < arguments.length) {
					arguments[index] = value;
					state = FormatState.READY_FOR_FORMAT;
				} else {
	                state = FormatState.NONE;
                }
			} else {
	            state = FormatState.NONE;
            }
		}  else if (state == FormatState.READY_FOR_FORMAT
		        && (seen == INVOKESPECIAL || seen == INVOKEVIRTUAL || seen == INVOKESTATIC || seen == INVOKEINTERFACE)
		        && stack.getStackDepth() == stackDepth) {

			String cl = getClassConstantOperand();
			String nm = getNameConstantOperand();
			String sig = getSigConstantOperand();
			XMethod m = getXMethodOperand();
			if ((m == null || m.isVarArgs()) && sig.indexOf("Ljava/lang/String;[Ljava/lang/Object;)") >= 0
					&& ("java/util/Formatter".equals(cl) && "format".equals(nm)
							|| "java/lang/String".equals(cl) && "format".equals(nm)
							|| "java/io/PrintStream".equals(cl) && "format".equals(nm)
							|| "java/io/PrintStream".equals(cl)  && "printf".equals(nm)
							|| cl.endsWith("Writer") && "format".equals(nm)
							|| cl.endsWith("Writer") && "printf".equals(nm))
							|| cl.endsWith("Logger") && nm.endsWith("fmt")) {

				try {
					String[] signatures = new String[arguments.length];
					for(int i = 0; i < signatures.length; i++)
						signatures[i] = arguments[i].getSignature();
					Formatter.check(formatString, signatures);
					
				} catch (IllegalFormatConversionException e) {
				
					if (e.getConversion() == 'b')
						bugReporter.reportBug(
								new BugInstance(this, "VA_FORMAT_STRING_BAD_CONVERSION_TO_BOOLEAN", HIGH_PRIORITY)
								.addClassAndMethod(this)
								.addCalledMethod(this)
								.addType(e.getArgumentSignature()).describe(TypeAnnotation.FOUND_ROLE)
								.addString(e.getFormatSpecifier()).describe(StringAnnotation.FORMAT_SPECIFIER_ROLE)
								.addString(formatString).describe(StringAnnotation.FORMAT_STRING_ROLE)
								.addValueSource(arguments[e.getArgIndex()], getMethod(), getPC())
								.addSourceLine(this)
							);
					else if (e.getArgumentSignature().charAt(0) == '[' && e.getConversion() == 's')
                	bugReporter.reportBug(
							new BugInstance(this, "VA_FORMAT_STRING_BAD_CONVERSION_FROM_ARRAY", HIGH_PRIORITY)
							.addClassAndMethod(this)
							.addCalledMethod(this)
							.addType(e.getArgumentSignature()).describe(TypeAnnotation.FOUND_ROLE)
							.addString(e.getFormatSpecifier()).describe(StringAnnotation.FORMAT_SPECIFIER_ROLE)
							.addString(formatString).describe(StringAnnotation.FORMAT_STRING_ROLE)
							.addValueSource(arguments[e.getArgIndex()], getMethod(), getPC())
							.addSourceLine(this)
						);
					else bugReporter.reportBug(
							new BugInstance(this, "VA_FORMAT_STRING_BAD_CONVERSION", HIGH_PRIORITY)
							.addClassAndMethod(this)
							.addCalledMethod(this)
							.addType(e.getArgumentSignature()).describe(TypeAnnotation.FOUND_ROLE)
							.addString(e.getFormatSpecifier()).describe(StringAnnotation.FORMAT_SPECIFIER_ROLE)
							.addString(formatString).describe(StringAnnotation.FORMAT_STRING_ROLE)
							.addValueSource(arguments[e.getArgIndex()], getMethod(), getPC())
							.addSourceLine(this)
						);
                } catch (IllegalArgumentException e) {
                	bugReporter.reportBug(
							new BugInstance(this, "VA_FORMAT_STRING_ILLEGAL", HIGH_PRIORITY)
							.addClassAndMethod(this)
							.addCalledMethod(this)
							.addString(formatString).describe(StringAnnotation.FORMAT_STRING_ROLE)
							.addSourceLine(this)
						);
                } catch (MissingFormatArgumentException e) {

                	if (e.pos < 0) {
	                    bugReporter.reportBug(
							new BugInstance(this, "VA_FORMAT_STRING_NO_PREVIOUS_ARGUMENT", HIGH_PRIORITY)
							.addClassAndMethod(this)
							.addCalledMethod(this)
							.addString(e.formatSpecifier).describe(StringAnnotation.FORMAT_SPECIFIER_ROLE)
							.addString(formatString).describe(StringAnnotation.FORMAT_STRING_ROLE)
							.addSourceLine(this)
						);
                    } else {
	                    bugReporter.reportBug(
	                    		new BugInstance(this, "VA_FORMAT_STRING_MISSING_ARGUMENT", HIGH_PRIORITY)
	                    		.addClassAndMethod(this)
	                    		.addCalledMethod(this)
	                    		.addString(e.formatSpecifier).describe(StringAnnotation.FORMAT_SPECIFIER_ROLE)
	                    		.addString(formatString).describe(StringAnnotation.FORMAT_STRING_ROLE)
	                    		.addInt(e.pos+1).describe(IntAnnotation.INT_EXPECTED_ARGUMENTS)
	                    		.addInt(arguments.length).describe(IntAnnotation.INT_ACTUAL_ARGUMENTS)
	                    		.addSourceLine(this)
	                    	);
                    }

                } catch (ExtraFormatArgumentsException e) {
                	int priority = NORMAL_PRIORITY;
                	String pattern = "VA_FORMAT_STRING_EXTRA_ARGUMENTS_PASSED";
                	if (e.used == 0) {
                		priority = HIGH_PRIORITY;
                		if (formatString.indexOf("{0") >= 0 || formatString.indexOf("{1") >= 0)
                			pattern = "VA_FORMAT_STRING_EXPECTED_MESSAGE_FORMAT_SUPPLIED";
                	}
                		
                	bugReporter.reportBug(
							new BugInstance(this, pattern, priority)
							.addClassAndMethod(this)
							.addCalledMethod(this)
							.addString(formatString).describe(StringAnnotation.FORMAT_STRING_ROLE)
							.addInt(e.used).describe(IntAnnotation.INT_EXPECTED_ARGUMENTS)
							.addInt(e.provided).describe(IntAnnotation.INT_ACTUAL_ARGUMENTS)
							.addSourceLine(this)
						);
                }
			}

		}
	}

	
 



}
