package apos.patcher.deob;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.objectweb.asm.Opcodes.IF_ICMPLE;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

public final class SwapConditionals
{

	private static int invertOp(final int opcode)
	{
		switch (opcode)
		{
			case IF_ICMPLT:
				return IF_ICMPGT;
			case IF_ICMPGT:
				return IF_ICMPLT;
			case IF_ICMPGE:
				return IF_ICMPLE;
			case IF_ICMPLE:
				return IF_ICMPGE;
			default:
				return opcode;
		}
	}

	static boolean isAccessedLabel(final AbstractInsnNode a, final InsnList code)
	{
		if (a instanceof LabelNode)
		{
			for (int j = 0; j < code.size(); ++j)
			{
				final AbstractInsnNode insn2 = code.get(j);
				if (!(insn2 instanceof JumpInsnNode))
				{
					continue;
				}
				if (((JumpInsnNode) insn2).label.equals(a))
				{
					return true;
				}
			}
		}
		return false;
	}

	public static int correctOrder(final InsnList code)
	{
		int count = 0;
		for (int i = 0; i < code.size(); ++i)
		{
			final AbstractInsnNode insn = code.get(i);
			switch (insn.getOpcode())
			{
				case IF_ICMPEQ:
				case IF_ICMPNE:
				case IF_ICMPLT:
				case IF_ICMPGT:
				case IF_ICMPGE:
				case IF_ICMPLE:
				case IF_ACMPEQ:
				case IF_ACMPNE:
					final StackNode n = Stack.buildMap(insn, 1);
					if (n == null)
					{
						continue;
					}
					if (n.children.size() != 2)
					{
//						System.out.printf(
//							"wrong number of children: %d (expected 2)%n",
//							n.children.size());
						continue;
					}

					final AbstractInsnNode push = n.children.get(0).insn;
					final IntPush ipush = IntPush.get(push);
					if (ipush != null)
					{
						if (IntPush.get(n.children.get(1).insn) != null)
						{
							continue;
						}
					}
					else
					{
						if (push.getOpcode() == ACONST_NULL)
						{
							if (n.children.get(1).insn.getOpcode() == ACONST_NULL)
							{
								continue;
							}
						}
						else
						{
							continue;
						}
					}

					// pure evil
					if (isAccessedLabel(n.children.get(1).insn.getPrevious(), code))
					{
						continue;
					}

					((JumpInsnNode) insn).setOpcode(invertOp(insn.getOpcode()));
					code.remove(push);
					code.insertBefore(insn, push);
					++count;
					break;
			}
		}

		return count;
	}

}
