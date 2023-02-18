package apos.patcher.deob;

import java.util.Map;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.NOP;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public final class Util
{

	static String[] OPCODES;
	static int LONGS_EQUAL = 0;
	static int SECOND_LONG_GREATER = 1;
	static int FIRST_LONG_GREATER = -1;

	static MethodNode getMethod(final ClassNode[] classes,
		final String owner, final String name, final String desc)
	{
		for (final ClassNode c : classes)
		{
			if (!c.name.equals(owner))
			{
				continue;
			}
			for (final MethodNode m : c.methods)
			{
				if (!m.name.equals(name)) continue;
				if (!m.desc.equals(desc)) continue;
				return m;
			}
			break;
		}
		return null;
	}

	static int free(int access)
	{
		if ((access & ACC_PUBLIC) == 0)
		{
			if ((access & ACC_PRIVATE) != 0)
			{
				access &= ~ACC_PRIVATE;
			}
			if ((access & ACC_PROTECTED) != 0)
			{
				access &= ~ACC_PROTECTED;
			}
			access |= ACC_PUBLIC;
		}
		if ((access & ACC_FINAL) != 0)
		{
			access &= ~ACC_FINAL;
		}
		return access;
	}

	static IntInsnNode getIntPush(final InsnList code, final int... integers)
	{
		final int size = code.size();
		for (int i = 0; i < size; ++i)
		{
			final AbstractInsnNode insn = code.get(i);
			if (!(insn instanceof IntInsnNode))
			{
				continue;
			}
			final IntInsnNode push = (IntInsnNode) insn;
			for (final int v : integers)
			{
				if (v == push.operand)
				{
					return push;
				}
			}
		}
		return null;
	}

	static void replaceInts(final InsnList code, final Map<Integer, Integer> map)
	{
		final int size = code.size();
		for (int i = 0; i < size; ++i)
		{
			final AbstractInsnNode insn = code.get(i);
			if (!(insn instanceof IntInsnNode))
			{
				continue;
			}
			final IntInsnNode push = (IntInsnNode) insn;
			final Integer integer = map.get(push.operand);
			if (integer != null)
			{
				push.operand = integer;
			}
		}
	}

	static AbstractInsnNode next(AbstractInsnNode insn)
	{
		do
		{
			insn = insn.getNext();
		} while (insn != null
			&& (insn instanceof LabelNode || insn.getOpcode() == NOP));
		return insn;
	}

	static AbstractInsnNode prev(AbstractInsnNode insn)
	{
		do
		{
			insn = insn.getPrevious();
		} while (insn != null
			&& (insn instanceof LabelNode || insn.getOpcode() == NOP));
		return insn;
	}

	static MethodNode getMethod(final ClassNode n, final String name, final String desc)
	{
		for (final MethodNode m : n.methods)
		{
			if (m.name.equals(name) && m.desc.equals(desc))
			{
				return m;
			}
		}
		return null;
	}

	static FieldNode getField(final ClassNode n, final String name, final String desc)
	{
		for (final FieldNode f : n.fields)
		{
			if (f.name.equals(name) && f.desc.equals(desc))
			{
				return f;
			}
		}
		return null;
	}

	static int indexOf(final InsnList list, final AbstractInsnNode insn)
	{
		for (int i = 0; i < list.size(); ++i)
		{
			if (list.get(i).equals(insn))
			{
				return i;
			}
		}
		return -1;
	}

	static
	{
		final String str = "NOP,ACONST_NULL,ICONST_M1,ICONST_0,ICONST_1,ICONST_2,ICONST_3,ICONST_4,ICONST_5,LCONST_0,LCONST_1,FCONST_0,FCONST_1,FCONST_2,DCONST_0,DCONST_1,BIPUSH,SIPUSH,LDC,,,ILOAD,LLOAD,FLOAD,DLOAD,ALOAD,,,,,,,,,,,,,,,,,,,,,IALOAD,LALOAD,FALOAD,DALOAD,AALOAD,BALOAD,CALOAD,SALOAD,ISTORE,LSTORE,FSTORE,DSTORE,ASTORE,,,,,,,,,,,,,,,,,,,,,IASTORE,LASTORE,FASTORE,DASTORE,AASTORE,BASTORE,CASTORE,SASTORE,POP,POP2,DUP,DUP_X1,DUP_X2,DUP2,DUP2_X1,DUP2_X2,SWAP,IADD,LADD,FADD,DADD,ISUB,LSUB,FSUB,DSUB,IMUL,LMUL,FMUL,DMUL,IDIV,LDIV,FDIV,DDIV,IREM,LREM,FREM,DREM,INEG,LNEG,FNEG,DNEG,ISHL,LSHL,ISHR,LSHR,IUSHR,LUSHR,IAND,LAND,IOR,LOR,IXOR,LXOR,IINC,I2L,I2F,I2D,L2I,L2F,L2D,F2I,F2L,F2D,D2I,D2L,D2F,I2B,I2C,I2S,LCMP,FCMPL,FCMPG,DCMPL,DCMPG,IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE,IF_ICMPEQ,IF_ICMPNE,IF_ICMPLT,IF_ICMPGE,IF_ICMPGT,IF_ICMPLE,IF_ACMPEQ,IF_ACMPNE,GOTO,JSR,RET,TABLESWITCH,LOOKUPSWITCH,IRETURN,LRETURN,FRETURN,DRETURN,ARETURN,RETURN,GETSTATIC,PUTSTATIC,GETFIELD,PUTFIELD,INVOKEVIRTUAL,INVOKESPECIAL,INVOKESTATIC,INVOKEINTERFACE,INVOKEDYNAMIC,NEW,NEWARRAY,ANEWARRAY,ARRAYLENGTH,ATHROW,CHECKCAST,INSTANCEOF,MONITORENTER,MONITOREXIT,,MULTIANEWARRAY,IFNULL,IFNONNULL,";
		OPCODES = new String[200];
		int i = 0;
		int k;
		for (int j = 0; (k = str.indexOf(',', j)) > 0; j = k + 1)
		{
			OPCODES[(i++)] = (j + 1 == k ? null : str.substring(j, k));
		}
	}
}
