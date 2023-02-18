package apos.patcher.deob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

public final class Stack
{

	static int getInsnReq(final AbstractInsnNode insn)
	{
		switch (insn.getOpcode())
		{
			case AALOAD:
			case SWAP:
			case SALOAD:
			case PUTFIELD:
			case LXOR:
			case LUSHR:
			case LSUB:
			case LSHR:
			case LSHL:
			case LREM:
			case LOR:
			case LMUL:
			case LDIV:
			case LCMP:
			case LAND:
			case LALOAD:
			case LADD:
			case IXOR:
			case IUSHR:
			case ISUB:
			case ISHR:
			case ISHL:
			case IREM:
			case IOR:
			case IMUL:
			case IF_ICMPNE:
			case IF_ICMPLT:
			case IF_ICMPLE:
			case IF_ICMPGT:
			case IF_ICMPGE:
			case IF_ICMPEQ:
			case IF_ACMPNE:
			case IF_ACMPEQ:
			case IDIV:
			case IAND:
			case IALOAD:
			case IADD:
			case FSUB:
			case FREM:
			case FMUL:
			case FDIV:
			case FCMPL:
			case FCMPG:
			case FALOAD:
			case FADD:
			case DUP_X1:
			case DSUB:
			case DREM:
			case DMUL:
			case DDIV:
			case DCMPL:
			case DCMPG:
			case DALOAD:
			case DADD:
			case CALOAD:
			case BALOAD:
				return 2;
			case AASTORE:
			case IASTORE:
			case FASTORE:
			case DUP_X2:
			case DASTORE:
			case CASTORE:
			case BASTORE:
			case SASTORE:
			case LASTORE:
				return 3;
			case ACONST_NULL:
			case GOTO:
			case GETSTATIC:
			case FLOAD:
			case FCONST_2:
			case FCONST_1:
			case FCONST_0:
			case DLOAD:
			case DCONST_1:
			case DCONST_0:
			case BIPUSH:
			case ALOAD:
			case LLOAD:
			case LDC:
			case LCONST_1:
			case LCONST_0:
			case JSR:
			case ILOAD:
			case IINC:
			case ICONST_5:
			case ICONST_4:
			case ICONST_3:
			case ICONST_2:
			case ICONST_1:
			case ICONST_0:
			case ICONST_M1:
			case SIPUSH:
			case RETURN:
			case RET:
			case NOP:
			case NEW:
				return 0;
			case ANEWARRAY:
			case IFGT:
			case IFGE:
			case IFNE:
			case IFEQ:
			case I2S:
			case I2L:
			case I2F:
			case I2D:
			case I2C:
			case I2B:
			case GETFIELD:
			case FSTORE:
			case FRETURN:
			case FNEG:
			case F2L:
			case F2I:
			case F2D:
			case DUP:
			case DSTORE:
			case DRETURN:
			case DNEG:
			case D2L:
			case D2I:
			case D2F:
			case CHECKCAST:
			case ATHROW:
			case ASTORE:
			case ARRAYLENGTH:
			case ARETURN:
			case TABLESWITCH:
			case PUTSTATIC:
			case POP:
			case NEWARRAY:
			case MONITOREXIT:
			case MONITORENTER:
			case LSTORE:
			case LRETURN:
			case LOOKUPSWITCH:
			case LNEG:
			case L2I:
			case L2F:
			case L2D:
			case ISTORE:
			case IRETURN:
			case INSTANCEOF:
			case INEG:
			case IFNULL:
			case IFNONNULL:
			case IFLT:
			case IFLE:
				return 1;
			case DUP2:
			case POP2:
			case DUP2_X2:
			case DUP2_X1:
				throw new RuntimeException(); // FIXME
			case INVOKEDYNAMIC:
			case INVOKESTATIC:
				return Type.getArgumentTypes(((MethodInsnNode) insn).desc).length;
			case INVOKEINTERFACE:
			case INVOKEVIRTUAL:
			case INVOKESPECIAL:
				return 1 + Type.getArgumentTypes(((MethodInsnNode) insn).desc).length;
			case MULTIANEWARRAY:
				return ((MultiANewArrayInsnNode) insn).dims;
			default:
				throw new IllegalArgumentException("" + insn.getOpcode());
		}
	}

	public static int getInsnResult(final AbstractInsnNode insn)
	{
		switch (insn.getOpcode())
		{
			case AALOAD:
			case SIPUSH:
			case SALOAD:
			case NEWARRAY:
			case NEW:
			case MULTIANEWARRAY:
			case LXOR:
			case LUSHR:
			case LSUB:
			case LSHR:
			case LSHL:
			case LREM:
			case LOR:
			case LNEG:
			case LMUL:
			case LLOAD:
			case LDIV:
			case LDC:
			case LCONST_1:
			case LCONST_0:
			case LCMP:
			case LAND:
			case LALOAD:
			case LADD:
			case L2I:
			case L2F:
			case L2D:
			case JSR:
			case IXOR:
			case IUSHR:
			case ISUB:
			case ISHR:
			case ISHL:
			case IREM:
			case IOR:
			case INSTANCEOF:
			case INEG:
			case IMUL:
			case ILOAD:
			case IDIV:
			case ICONST_5:
			case ICONST_4:
			case ICONST_3:
			case ICONST_2:
			case ICONST_1:
			case ICONST_0:
			case ICONST_M1:
			case IAND:
			case IALOAD:
			case IADD:
			case I2S:
			case I2L:
			case I2F:
			case I2D:
			case I2C:
			case I2B:
			case GETSTATIC:
			case GETFIELD:
			case FSUB:
			case FREM:
			case FNEG:
			case FMUL:
			case FLOAD:
			case FDIV:
			case FCONST_2:
			case FCONST_1:
			case FCONST_0:
			case FCMPL:
			case FCMPG:
			case FALOAD:
			case FADD:
			case F2L:
			case F2I:
			case F2D:
			case DSUB:
			case DREM:
			case DNEG:
			case DMUL:
			case DLOAD:
			case DDIV:
			case DCONST_1:
			case DCONST_0:
			case DCMPL:
			case DCMPG:
			case DALOAD:
			case DADD:
			case D2L:
			case D2I:
			case D2F:
			case CHECKCAST:
			case CALOAD:
			case BIPUSH:
			case BALOAD:
			case ARRAYLENGTH:
			case ANEWARRAY:
			case ALOAD:
			case ACONST_NULL:
				return 1;
			case AASTORE:
			case TABLESWITCH:
			case SASTORE:
			case RET:
			case PUTSTATIC:
			case PUTFIELD:
			case POP2:
			case POP:
			case NOP:
			case MONITOREXIT:
			case MONITORENTER:
			case LSTORE:
			case LOOKUPSWITCH:
			case LASTORE:
			case ISTORE:
			case IINC:
			case IFNULL:
			case IFNONNULL:
			case IFLT:
			case IFLE:
			case IFGT:
			case IFGE:
			case IFNE:
			case IFEQ:
			case IF_ICMPNE:
			case IF_ICMPLT:
			case IF_ICMPLE:
			case IF_ICMPGT:
			case IF_ICMPGE:
			case IF_ICMPEQ:
			case IF_ACMPNE:
			case IF_ACMPEQ:
			case IASTORE:
			case GOTO:
			case FSTORE:
			case FASTORE:
			case DSTORE:
			case DASTORE:
			case CASTORE:
			case BASTORE:
			case ASTORE:
				return 0;
			case ARETURN:
			case RETURN:
			case LRETURN:
			case IRETURN:
			case FRETURN:
			case DRETURN:
			case ATHROW:
				throw new RuntimeException("stack emptied");
			case DUP:
			case SWAP:
				return 2;
			case DUP_X1:
				return 3;
			case DUP_X2:
			case DUP2_X2:
			case DUP2_X1:
			case DUP2:
				throw new RuntimeException(); // FIXME
			case INVOKEDYNAMIC:
			case INVOKEVIRTUAL:
			case INVOKESTATIC:
			case INVOKESPECIAL:
			case INVOKEINTERFACE:
				return (Type.getReturnType(((MethodInsnNode) insn).desc) == Type.VOID_TYPE ? 0
					: 1);
			default:
				throw new IllegalArgumentException("" + insn.getOpcode());
		}
	}

	static int getDifference(final AbstractInsnNode insn)
	{
		return getInsnResult(insn) - getInsnReq(insn);
	}

	static ArrayList<StackNode> createList(final AbstractInsnNode origin)
	{
		final ArrayList<StackNode> list = new ArrayList<>();
		final int req = getInsnReq(origin);

		if (req == 0)
		{
			list.add(new StackNode(origin));
			return list;
		}

		int owed = -req;

		list.add(new StackNode(origin));
		for (AbstractInsnNode p = Util.prev(origin); p != null; p = Util.prev(p))
		{
			list.add(new StackNode(p));
			owed += getDifference(p);
			if (owed == 0)
			{
				break;
			}
		}

		Collections.reverse(list);
		return list;
	}

	private static StackNode getNode(final List<StackNode> list,
		final AbstractInsnNode origin)
	{
		for (final StackNode n : list)
		{
			if (n.insn.equals(origin))
			{
				return n;
			}
		}
		throw new IllegalArgumentException("insn not in list");
	}

	private static void buildTree(final List<StackNode> list, final StackNode origin,
		final int deepness, final int max)
	{
		final int req = getInsnReq(origin.insn);

		if (req == 0)
		{
			return;
		}

		int owed = -req;

		for (AbstractInsnNode p = Util.prev(origin.insn); p != null; p = Util.prev(p))
		{
			final StackNode child = getNode(list, p);
			child.parent = origin;
			origin.children.add(child);
			owed += getDifference(p);
			if (owed == 0)
			{
				break;
			}
		}

		Collections.reverse(origin.children);

		if (deepness < max)
		{
			for (final StackNode n : origin.children)
			{
				buildTree(list, n, deepness + 1, max);
			}

			origin.children.removeIf(n -> !origin.equals(n.parent));
		}
	}

	static void print(final List<StackNode> list)
	{
		System.out.println("{");
		for (final StackNode n : list)
		{
			System.out.println("\t" + Util.OPCODES[n.insn.getOpcode()]);
		}
		System.out.println("}");
	}

	static StackNode buildMap(final AbstractInsnNode insn, final int maxlevels)
	{
		try
		{
			final ArrayList<StackNode> list = createList(insn);
			for (final StackNode n : list)
			{
				final int op = n.insn.getOpcode();
				if (op == GOTO || op == DUP || op == DUP_X1)
				{
					// print(list);
					// System.out.println("^ Failed to analyze");
					return null; // FIXME
				}
			}
			final StackNode origin = list.get(list.size() - 1);
			buildTree(list, origin, 0, maxlevels);
			return origin;
		}
		catch (final Throwable t)
		{
			// FIXME: this happens when we hit an unsupported opcode in
			// createList()
			return null;
		}
	}

}
