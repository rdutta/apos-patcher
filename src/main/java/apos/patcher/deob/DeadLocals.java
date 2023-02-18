package apos.patcher.deob;

import java.util.ArrayList;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.ISTORE;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class DeadLocals
{

	static boolean findLoad(final InsnList list, final int var, final int start, final int end)
	{
		for (int i = start; i < end; ++i)
		{
			final AbstractInsnNode insn = list.get(i);
			if (insn instanceof JumpInsnNode)
			{
				final JumpInsnNode jump = (JumpInsnNode) insn;
				if (findLoad(list, var, Util.indexOf(list, jump.label), start))
				{
					return true;
				}
			}
			else if (insn.getOpcode() == ILOAD
				&& ((VarInsnNode) insn).var == var)
			{
				return true;
			}
		}
		return false;
	}

	static boolean shouldRemove(final ArrayList<StackNode> list, final VarInsnNode origin)
	{
		for (final StackNode n : list)
		{
			if (n.insn instanceof MethodInsnNode
				|| n.insn instanceof InvokeDynamicInsnNode
				|| n.insn instanceof JumpInsnNode
				|| n.insn instanceof LabelNode)
			{
				return false;
			}
		}

		return true;
	}

	public static int remove(final InsnList list)
	{
		int removed = 0;
		for (int i = 0; i < list.size(); ++i)
		{
			final AbstractInsnNode insn = list.get(i);
			if (insn.getOpcode() != ISTORE)
			{
				continue;
			}
			final VarInsnNode var = (VarInsnNode) insn;
			if (findLoad(list, var.var, i, list.size()))
			{
				continue;
			}
			try
			{
				final ArrayList<StackNode> stack = Stack.createList(var);
				if (shouldRemove(stack, var))
				{
					for (final StackNode n : stack)
					{
						list.remove(n.insn);
					}
					++removed;
				}
			}
			catch (final Throwable t)
			{
				t.printStackTrace();
			}
		}
		return removed;
	}

}
