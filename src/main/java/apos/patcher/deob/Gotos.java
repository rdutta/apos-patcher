package apos.patcher.deob;

import static org.objectweb.asm.Opcodes.GOTO;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;

public class Gotos
{

	public static int fold(final InsnList code)
	{
		// fold GOTOs that target GOTOs
		// or remove those which target the next instruction
		int count = 0;

		for (int i = 0; i < code.size(); ++i)
		{
			final AbstractInsnNode insn = code.get(i);
			JumpInsnNode jump;

			if (insn.getOpcode() != GOTO)
			{
				continue;
			}

			jump = (JumpInsnNode) insn;

			if (Util.next(jump.label) == Util.next(jump))
			{
				code.remove(jump);
				--i;
				++count;
				continue;
			}

			final AbstractInsnNode next = Util.next(jump.label);

			if (next.getOpcode() == GOTO)
			{
				jump = (JumpInsnNode) next;
				code.set(insn, new JumpInsnNode(GOTO, jump.label));
				i = 0;
				++count;
			}
		}

		return count;
	}

}
