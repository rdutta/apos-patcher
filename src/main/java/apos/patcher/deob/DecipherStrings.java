package apos.patcher.deob;

import java.util.ArrayList;
import java.util.Objects;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

public final class DecipherStrings
{

	private static int[] getKeys(final MethodNode z2)
	{
		for (int i = 0; i < z2.instructions.size(); ++i)
		{
			final AbstractInsnNode instr = z2.instructions.get(i);
			if (!(instr instanceof TableSwitchInsnNode))
			{
				continue;
			}
			final TableSwitchInsnNode ts = (TableSwitchInsnNode) instr;
			final int[] keys = new int[ts.labels.size() + 1];
			for (int j = 0; j < ts.labels.size(); j++)
			{
				keys[j] = Objects.requireNonNull(IntPush.get(ts.labels.get(j).getNext())).val;
			}
			keys[keys.length - 1] = Objects.requireNonNull(IntPush.get(ts.dflt.getNext())).val;
			if (keys.length != 5)
			{
				return null;
			}
			for (final int key : keys)
			{
				if (key == 0)
				{
					return null;
				}
			}
			return keys;
		}
		return null;
	}

	private static String decipher(final String enc, final int[] keys)
	{
		final char[] c = enc.toCharArray();
		for (int i = 0; i < c.length; ++i)
		{
			c[i] = (char) (c[i] ^ keys[i % keys.length]);
		}
		return new String(c);
	}

	public static int decipher(final ClassNode c)
	{
		int count = 0;
		final MethodNode z1 = Util.getMethod(c, "z", "(Ljava/lang/String;)[C");
		final MethodNode z2 = Util.getMethod(c, "z", "([C)Ljava/lang/String;");
		if (z1 == null || z2 == null)
		{
			return 0;
		}
		final int[] keys = getKeys(z2);
		if (keys == null)
		{
			return 0;
		}
		final MethodNode clinit = Util.getMethod(c, "<clinit>", "()V");
		if (clinit == null)
		{
			return 0;
		}
		final ArrayList<String> decrypted = new ArrayList<>();
		FieldInsnNode storage = null;
		for (int i = 0; i < clinit.instructions.size(); i++)
		{
			final AbstractInsnNode insn = clinit.instructions.get(i);
			if (!(insn instanceof LdcInsnNode))
			{
				continue;
			}
			final LdcInsnNode ldc = (LdcInsnNode) insn;
			if (!(ldc.cst instanceof String))
			{
				continue;
			}
			final AbstractInsnNode insn2 = insn.getNext();
			final AbstractInsnNode insn3 = insn2.getNext();
			if (!(insn2 instanceof MethodInsnNode)
				|| !(insn3 instanceof MethodInsnNode))
			{
				continue;
			}
			final MethodInsnNode m1 = (MethodInsnNode) insn2;
			final MethodInsnNode m2 = (MethodInsnNode) insn3;
			if (!m1.owner.equals(c.name) || !m1.name.equals(z1.name)
				|| !m1.desc.equals(z1.desc) || !m2.owner.equals(c.name)
				|| !m2.name.equals(z2.name) || !m2.desc.equals(z2.desc))
			{
				continue;
			}
			final String str = decipher((String) ldc.cst, keys);
			decrypted.add(str);
			if (storage == null)
			{
				for (AbstractInsnNode x = Util.next(insn3); x != null; x = Util
					.next(x))
				{
					if (x.getOpcode() != PUTSTATIC)
					{
						continue;
					}
					final FieldInsnNode fin = (FieldInsnNode) x;
					if (fin.desc.equals("[Ljava/lang/String;")
						|| fin.desc.equals("Ljava/lang/String;"))
					{
						storage = fin;
					}
					break;
				}
			}
			clinit.instructions.set(ldc, new LdcInsnNode(str));
			clinit.instructions.remove(m1);
			clinit.instructions.remove(m2);
			++count;
		}
		c.methods.remove(z1);
		c.methods.remove(z2);
		if (storage == null)
		{
			return 0;
		}
		for (int i = 0; i < clinit.instructions.size(); i++)
		{
			final AbstractInsnNode insn = clinit.instructions.get(i);
			if (insn.getOpcode() != PUTSTATIC)
			{
				continue;
			}
			final FieldInsnNode f = (FieldInsnNode) insn;
			if (!f.owner.equals(c.name) || !f.name.equals(storage.name)
				|| !f.desc.equals(storage.desc))
			{
				continue;
			}
			for (final StackNode n : Stack.createList(f))
			{
				clinit.instructions.remove(n.insn);
			}
			final FieldNode field = Util.getField(c, storage.name, storage.desc);
			if (field == null)
			{
				System.out.printf(
					"warning: string storage field not found: %s %s:%s%n",
					storage.desc, c.name, storage.name);
			}
			else
			{
				c.fields.remove(field);
			}
			break;
		}
		for (final MethodNode m : c.methods)
		{
			for (int i = 0; i < m.instructions.size(); i++)
			{
				final AbstractInsnNode insn = m.instructions.get(i);
				if (insn.getOpcode() != GETSTATIC)
				{
					continue;
				}
				final FieldInsnNode fin = (FieldInsnNode) insn;
				if (!fin.owner.equals(storage.owner)
					|| !fin.name.equals(storage.name)
					|| !fin.desc.equals(storage.desc))
				{
					continue;
				}
				if (storage.desc.equals("Ljava/lang/String;"))
				{
					m.instructions.set(insn, new LdcInsnNode(decrypted.get(0)));
				}
				else
				{
					final AbstractInsnNode integer = fin.getNext();
					final String dec = decrypted.get(Objects.requireNonNull(IntPush.get(integer)).val);
					m.instructions.remove(integer.getNext()); // AALOAD
					m.instructions.remove(integer);
					m.instructions.set(insn, new LdcInsnNode(dec));
				}
				++count;
			}
		}
		return count;
	}

}
