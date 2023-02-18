package apos.patcher.deob;

import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.ISHL;
import static org.objectweb.asm.Opcodes.ISHR;
import static org.objectweb.asm.Opcodes.ISUB;
import static org.objectweb.asm.Opcodes.IUSHR;
import static org.objectweb.asm.Opcodes.LDC;
import static org.objectweb.asm.Opcodes.LSHL;
import static org.objectweb.asm.Opcodes.LSHR;
import static org.objectweb.asm.Opcodes.LUSHR;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

public class Arithmetics
{

	public static int simplify(final InsnList list)
	{
		int count = 0;

		for (int i = 1; i < list.size(); ++i)
		{
			final AbstractInsnNode insn = list.get(i);
			final AbstractInsnNode prev = Util.prev(insn);

			final int op = insn.getOpcode();
			IntPush push;

			switch (op)
			{
				case ISHL:
				case LSHL:
				case ISHR:
				case LSHR:
				case IUSHR:
				case LUSHR:
					if (prev.getOpcode() == LDC)
					{
						final int cst = (Integer) ((LdcInsnNode) prev).cst;
						final int mask = op == ISHL || op == ISHR || op == IUSHR ? 31
							: 63;
						list.set(prev, new IntPush(cst & mask).insn);
						++count;
					}
					break;
				case IADD:
					push = IntPush.get(prev);
					if (push != null)
					{
						if (push.val < 0)
						{
							list.set(prev, new IntPush(-push.val).insn);
							list.set(insn, new InsnNode(ISUB));
							++count;
						}
					}
					else
					{
						final StackNode n = Stack.buildMap(insn, 1);
						if (n == null)
						{
							break;
						}
						push = IntPush.get(n.children.get(0).insn);
						if (push != null && push.val < 0)
						{
							list.insertBefore(insn, new IntPush(-push.val).insn);
							list.set(insn, new InsnNode(ISUB));
							list.remove(push.insn);
							++count;
						}
					}
					break;
				case ISUB:
					push = IntPush.get(prev);
					if (push != null)
					{
						if (push.val < 0)
						{
							list.set(prev, new IntPush(-push.val).insn);
							list.set(insn, new InsnNode(IADD));
							++count;
						}
					}
					break;
			}
		}

		return count;
	}

}
