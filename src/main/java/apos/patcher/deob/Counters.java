package apos.patcher.deob;

import java.util.Collection;
import java.util.Iterator;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class Counters
{

	public static int remove(final Collection<ClassNode> classNodes)
	{
		int count = 0;

		for (final ClassNode c : classNodes)
		{
			for (final MethodNode m : c.methods)
			{
				for (int i = 0; i < m.instructions.size(); ++i)
				{
					if (m.instructions.get(i).getOpcode() != GETSTATIC)
					{
						continue;
					}

					final FieldInsnNode getstatic = (FieldInsnNode) m.instructions.get(i);

					if (!getstatic.owner.equals(c.name))
					{
						continue;
					}

					final AbstractInsnNode iconst = getstatic.getNext();

					if (iconst.getOpcode() != ICONST_1)
					{
						continue;
					}

					final AbstractInsnNode iadd = iconst.getNext();

					if (iadd.getOpcode() != IADD)
					{
						continue;
					}

					if (iadd.getNext().getOpcode() != PUTSTATIC)
					{
						continue;
					}

					final FieldRef ref = new FieldRef(c.name, getstatic.name, getstatic.desc);

					final FieldInsnNode putstatic = (FieldInsnNode) iadd.getNext();

					if (!ref.equalsInsn(putstatic))
					{
						continue;
					}

					if (findGets(ref, getstatic, putstatic, classNodes))
					{
						continue;
					}

					m.instructions.remove(getstatic);
					m.instructions.remove(iconst);
					m.instructions.remove(iadd);
					m.instructions.remove(putstatic);
					removeField(c, ref);
					++count;
				}
			}
		}

		return count;
	}

	private static void removeField(final ClassNode c, final FieldRef ref)
	{
		final Iterator<FieldNode> it = c.fields.iterator();

		while (it.hasNext())
		{
			final FieldNode f = it.next();

			if (f.name.equals(ref.name) && f.desc.equals(ref.desc))
			{
				it.remove();
				return;
			}
		}
	}

	private static boolean findGets(final FieldRef ref, final FieldInsnNode get,
		final FieldInsnNode put, final Collection<ClassNode> classNodes)
	{
		for (final ClassNode n : classNodes)
		{
			for (final MethodNode m : n.methods)
			{
				for (int i = 0; i < m.instructions.size(); ++i)
				{
					final AbstractInsnNode insn = m.instructions.get(i);

					if (!(insn instanceof FieldInsnNode))
					{
						continue;
					}

					final FieldInsnNode f = (FieldInsnNode) insn;

					if (ref.equalsInsn(f) && f != get && f != put)
					{
						return true;
					}
				}
			}
		}

		return false;
	}

}
