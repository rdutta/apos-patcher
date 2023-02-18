package apos.patcher.deob;

import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class TryCatch
{

	public static int removeRedundantTryCatchBlocks(final MethodNode methodNode)
	{
		int count = 0;

		for (int i = 0; i < methodNode.tryCatchBlocks.size(); ++i)
		{
			final TryCatchBlockNode block = methodNode.tryCatchBlocks.get(i);
			final AbstractInsnNode start = block.handler.getNext();

			if (start.getOpcode() == ATHROW)
			{
				final AbstractInsnNode prev;

				prev = block.handler.getPrevious();
				methodNode.instructions.remove(start);
				methodNode.tryCatchBlocks.remove(i);

				if (prev instanceof JumpInsnNode)
				{
					if (((JumpInsnNode) prev).label == block.handler)
					{
						methodNode.instructions.remove(prev);
					}
				}

				--i;
				++count;
			}
		}

		for (int i = 0; i < (methodNode.tryCatchBlocks.size() - 1); ++i)
		{
			final TryCatchBlockNode cur = methodNode.tryCatchBlocks.get(i);
			final TryCatchBlockNode next = methodNode.tryCatchBlocks.get(i + 1);

			if (cur.type == null || next.type == null)
			{
				continue;
			}

			if (cur.handler == next.handler)
			{
				cur.end = next.end;
				methodNode.tryCatchBlocks.remove(i + 1);
				++count;
				--i;
			}
		}

		return count;
	}

	public static int removeRuntimeExceptionBlocks(final MethodNode methodNode)
	{
		int count = 0;

		tcloop:
		for (int i = 0; i < methodNode.tryCatchBlocks.size(); ++i)
		{
			final TryCatchBlockNode block = methodNode.tryCatchBlocks.get(i);
			AbstractInsnNode insn;

			for (insn = block.start; insn != block.end; insn = insn.getNext())
			{
				if (insn instanceof TypeInsnNode &&
					insn.getOpcode() == NEW &&
					((TypeInsnNode) insn).desc.equals("java/lang/RuntimeException"))
				{
					continue tcloop;
				}
			}

			if (!"java/lang/RuntimeException".equals(block.type))
			{
				continue;
			}

			for (insn = block.handler; insn != null; insn = insn.getNext())
			{
				if (insn.getOpcode() == ATHROW)
				{
					break;
				}

				switch (insn.getOpcode())
				{
					case IRETURN:
					case LRETURN:
					case FRETURN:
					case DRETURN:
					case ARETURN:
					case RETURN:
						continue tcloop;
				}
			}

			methodNode.tryCatchBlocks.remove(i--);
			++count;
		}

		return count;
	}

}

