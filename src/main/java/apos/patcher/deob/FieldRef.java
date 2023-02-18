package apos.patcher.deob;

import java.util.Collection;
import java.util.Objects;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ISTORE;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

public final class FieldRef
{

	final String owner;
	final String name;
	final String desc;

	public FieldRef(final String owner, final String name, final String desc)
	{
		this.owner = owner;
		this.name = name;
		this.desc = desc;
	}

	boolean equalsInsn(final FieldInsnNode n)
	{
		return this.owner.equals(n.owner) && this.name.equals(n.name) && this.desc.equals(n.desc);
	}

	public static FieldRef getControlField(final Collection<ClassNode> classes)
	{
		ClassNode client = null;

		for (final ClassNode c : classes)
		{
			if (!c.name.equals("client"))
			{
				continue;
			}

			client = c;
			break;
		}

		if (client != null)
		{
			for (final MethodNode m : client.methods)
			{
				if ((m.access & ACC_STATIC) != 0)
				{
					continue;
				}

				final InsnList list = m.instructions;

				for (int i = 1; i < list.size(); i++)
				{
					final AbstractInsnNode insn = list.get(i);
					final AbstractInsnNode prev = insn.getPrevious();

					if (insn.getOpcode() != ISTORE || prev.getOpcode() != GETSTATIC)
					{
						continue;
					}

					final FieldInsnNode gs = (FieldInsnNode) list.get(i - 1);

					for (final FieldNode f : client.fields)
					{
						if (!Objects.equals(f.name, gs.name) || !Objects.equals(f.desc, gs.desc))
						{
							continue;
						}

						if (!f.desc.equals("Z") && !f.desc.equals("I"))
						{
							throw new RuntimeException("Control field wrong type: " + f.desc);
						}

						return new FieldRef("client", f.name, f.desc);
					}
				}
			}
		}

		return null;
	}

}
