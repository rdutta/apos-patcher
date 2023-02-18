package apos.patcher;

import apos.patcher.deob.Arithmetics;
import apos.patcher.deob.ConditionalNot;
import apos.patcher.deob.Counters;
import apos.patcher.deob.DeadLocals;
import apos.patcher.deob.DecipherStrings;
import apos.patcher.deob.FieldRef;
import apos.patcher.deob.Gotos;
import apos.patcher.deob.OpaquePredicates;
import apos.patcher.deob.SwapConditionals;
import apos.patcher.deob.TryCatch;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

@Slf4j
public final class Main
{

	private static final Path OUT_DIR = Paths.get("out");
	private static final Path CLIENT_JAR = Paths.get("rsclassic.jar");
	private static final Path PATCHED_CLIENT_JAR = OUT_DIR.resolve("rsclassic.jar");

	private static final String DEBUG_COLOR = "@cya@";

	private Main()
	{
	}

	private static final class DeobCounters
	{

		static int removedRTC;
		static int removedREB;
		static int removedPreds;
		static int foldedGotos;
		static int simplifiedArthimetic;
		static int removedConditionalNots;
		static int swappedConditionals;
		static int removedDeadLocals;
		static int removedCounters;
		static int decipheredStrings;

	}

	public static void main(final String[] args) throws Throwable
	{
		log.info("Running patcher ...");

		final String fileName = CLIENT_JAR.getFileName().toString();

		URL url = Main.class.getClassLoader().getResource(fileName);

		if (url == null)
		{
			log.error("Could not locate: {}", CLIENT_JAR);
			System.exit(1);
		}

		log.info("Input: {}", url.getPath());

		if (Files.exists(PATCHED_CLIENT_JAR))
		{
			Files.delete(PATCHED_CLIENT_JAR);
			log.info("Deleted file: {}", PATCHED_CLIENT_JAR);
		}

		if (!Files.exists(OUT_DIR))
		{
			final Path path = Files.createDirectory(OUT_DIR);
			log.info("Created dir: {}", path);
		}

		log.info("Reading class files from JAR");

		final Collection<ClassNode> classNodes = readClassFiles(url.toURI());

		if (classNodes.isEmpty())
		{
			log.error("Failed to read class files from JAR");
			System.exit(1);
		}

		log.info("Deobfuscating {} classes", classNodes.size());
		deobfuscateClasses(classNodes);

		log.info("Removed {} redundant try catch blocks", DeobCounters.removedRTC);
		log.info("Removed {} runtime exception blocks", DeobCounters.removedREB);
		log.info("Removed {} opaque predicates", DeobCounters.removedPreds);
		log.info("Folded {} goto instructions", DeobCounters.foldedGotos);
		log.info("Simplified {} arithmetics", DeobCounters.simplifiedArthimetic);
		log.info("Removed {} conditional nots", DeobCounters.removedConditionalNots);
		log.info("Swapped {} conditionals", DeobCounters.swappedConditionals);
		log.info("Removed {} dead locals", DeobCounters.removedDeadLocals);
		log.info("Removed {} counters", DeobCounters.removedCounters);
		log.info("Inlined {} deciphered strings", DeobCounters.decipheredStrings);

		log.info("Patching {} classes", classNodes.size());
		patchClasses(classNodes);

		log.info("Writing classes to file");
		writeClasses(classNodes);

		log.info("Output: {}", PATCHED_CLIENT_JAR.toAbsolutePath());
		log.info("Finished");
	}

	private static Collection<ClassNode> readClassFiles(final URI uri) throws IOException
	{
		final List<ClassNode> classNodes = new ArrayList<>();

		try (final JarFile jarFile = new JarFile(new File(uri)))
		{
			final Enumeration<JarEntry> enumeration = jarFile.entries();

			while (enumeration.hasMoreElements())
			{
				final JarEntry jarEntry = enumeration.nextElement();

				final String name = jarEntry.getName();

				if (!name.endsWith(".class"))
				{
					continue;
				}

				try (final InputStream inputStream = jarFile.getInputStream(jarEntry))
				{
					final int size = (int) jarEntry.getSize();

					final byte[] bytes = new byte[size];

					int read = 0;

					do
					{
						read += inputStream.read(bytes, read, size - read);
					} while (read < size);

					final ClassReader classReader = new ClassReader(bytes);
					final ClassNode classNode = new ClassNode();

					classReader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					classNodes.add(classNode);
				}
			}
		}

		classNodes.sort(Comparator.comparing(cn -> cn.name));

		return classNodes;
	}

	private static void writeClasses(final Collection<ClassNode> classNodes) throws IOException
	{
		try (final JarOutputStream jos = new JarOutputStream(Files.newOutputStream(PATCHED_CLIENT_JAR.toFile().toPath())))
		{
			for (final ClassNode classNode : classNodes)
			{
				final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
				classNode.accept(classWriter);

				final JarEntry newEntry = new JarEntry(classNode.name + ".class");

				jos.putNextEntry(newEntry);
				jos.write(classWriter.toByteArray());
				jos.closeEntry();
			}
		}
	}

	private static void deobfuscateClasses(final Collection<ClassNode> classNodes)
	{
		final FieldRef clientField = FieldRef.getControlField(classNodes);

		if (clientField == null)
		{
			log.error("Could not locate clientField.");
			System.exit(1);
		}

		for (final ClassNode classNode : classNodes)
		{
			for (final MethodNode methodNode : classNode.methods)
			{
				DeobCounters.removedRTC += TryCatch.removeRedundantTryCatchBlocks(methodNode);
				DeobCounters.removedREB += TryCatch.removeRuntimeExceptionBlocks(methodNode);
				DeobCounters.removedPreds += OpaquePredicates.remove(clientField, methodNode.instructions);
				DeobCounters.foldedGotos += Gotos.fold(methodNode.instructions);
				DeobCounters.simplifiedArthimetic += Arithmetics.simplify(methodNode.instructions);
				DeobCounters.removedConditionalNots += ConditionalNot.remove(methodNode.instructions);
				DeobCounters.swappedConditionals += SwapConditionals.correctOrder(methodNode.instructions);
				DeobCounters.removedDeadLocals += DeadLocals.remove(methodNode.instructions);
				addParamNodes(classNode, methodNode);
			}

			DeobCounters.decipheredStrings += DecipherStrings.decipher(classNode);
		}

		DeobCounters.removedCounters += Counters.remove(classNodes);
	}

	private static void addParamNodes(final ClassNode classNode, final MethodNode methodNode)
	{
		final Type[] types = Type.getArgumentTypes(methodNode.desc);

		final LabelNode l1 = new LabelNode();
		final LabelNode l2 = new LabelNode();

		if (methodNode.localVariables == null)
		{
			methodNode.localVariables = new ArrayList<>();
		}
		if (methodNode.instructions.getFirst() != null)
		{
			methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), l1);
			methodNode.instructions.insert(methodNode.instructions.getLast(), l2);
		}
		else
		{
			methodNode.instructions.add(l1);
			methodNode.instructions.add(l2);
		}

		int index = 0;

		if ((methodNode.access & Opcodes.ACC_STATIC) == 0)
		{
			methodNode.localVariables.add(new LocalVariableNode("this", "L" + classNode.name + ";",
				null, l1, l2, index++));
		}

		for (int i = 0; i < types.length; ++i)
		{
			methodNode.localVariables.add(new LocalVariableNode("arg" + i, types[i].getDescriptor(),
				null, l1, l2, index++));
		}
	}

	private static void patchClasses(final Collection<ClassNode> classNodes)
	{
		for (final ClassNode classNode : classNodes)
		{
			patchAccessors(classNode);
			hookFields(classNode);

			switch (classNode.name)
			{
				case "cb":
					patchClass_cb(classNode);
					break;
				case "client":
					patchClient(classNode);
					break;
				case "e":
					patchApplet(classNode);
					break;
				case "gb":
					patchClass_gb(classNode);
					break;
				case "lb":
					patchRendererHelper(classNode);
					break;
				case "p":
					patchClass_p(classNode);
					break;
				case "t":
					patchClass_t(classNode);
					break;
				case "ua":
					patchRenderer(classNode);
					break;
				case "wb":
					patchClass_wb(classNode);
					break;
				default:
					break;
			}
		}
	}

	private static void patchAccessors(final ClassNode classNode)
	{
		if ((classNode.access & Opcodes.ACC_PUBLIC) != Opcodes.ACC_PUBLIC)
		{
			classNode.access = classNode.access | Opcodes.ACC_PUBLIC;
		}

		for (final FieldNode fieldNode : classNode.fields)
		{
			if ((fieldNode.access & Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE)
			{
				fieldNode.access = (fieldNode.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
			}
			else if ((fieldNode.access & Opcodes.ACC_PUBLIC) != Opcodes.ACC_PUBLIC)
			{
				fieldNode.access = fieldNode.access | Opcodes.ACC_PUBLIC;
			}
		}

		for (final MethodNode methodNode : classNode.methods)
		{
			if ((methodNode.access & Opcodes.ACC_PROTECTED) != Opcodes.ACC_PROTECTED)
			{
				if ((methodNode.access & Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE)
				{
					methodNode.access = (methodNode.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
				}
				else if ((methodNode.access & Opcodes.ACC_PUBLIC) != Opcodes.ACC_PUBLIC)
				{
					methodNode.access = methodNode.access | Opcodes.ACC_PUBLIC;
				}
			}
		}
	}

	private static void hookFields(final ClassNode classNode)
	{
		for (final MethodNode methodNode : classNode.methods)
		{
			rsaFieldsHook(methodNode);
			cameraFieldsHook(methodNode);
		}
	}

	private static void patchClass_cb(final ClassNode classNode)
	{
		log.info("Patching class: cb");

		for (final MethodNode methodNode : classNode.methods)
		{
			if (methodNode.name.equals("a"))
			{
				if (methodNode.desc.equals("(IIIBIIII[I[IIIIIII)V"))
				{
					renderTexturesHook(methodNode);
				}
				else if (methodNode.desc.equals("(Ljava/net/URL;Le;I)V"))
				{
					loadContentCrcsHook(methodNode);
				}
			}
		}
	}

	private static void patchClient(final ClassNode classNode)
	{
		log.info("Patching class: client");

		classNode.access = classNode.access & ~Opcodes.ACC_FINAL;

		for (final MethodNode methodNode : classNode.methods)
		{
			switch (methodNode.name)
			{
				case "A":
					if (methodNode.desc.equals("(I)V"))
					{
						paintHook(methodNode);
					}
					break;
				case "a":
					switch (methodNode.desc)
					{
						case "(Ljava/lang/String;I)V":
							commandHook(methodNode);
							break;
						case "(B)V":
							removeURLCheck(methodNode);
							cameraViewDistanceCrashFix(methodNode);
							initHook(methodNode);
							break;
						case "(ZLjava/lang/String;ILjava/lang/String;IILjava/lang/String;Ljava/lang/String;)V":
							gameMessageHook(methodNode);
							break;
						case "(IIIBII)Lta;":
							npcSpawnedHook(methodNode);
							break;
						case "(II)V":
							showReportPlayerHook(methodNode);
							break;
						case "(IZ)V":
							debugInventoryItemHook(methodNode);
							break;
						case "(ZI)V":
							closeDuelAndTradeFix(methodNode);
							break;
						case "(ZZ)V":
							friendsListFix(methodNode);
							friendsListRemove(methodNode);
							break;
						default:
							break;
					}
					break;
				case "b":
					switch (methodNode.desc)
					{
						case "(BLjava/lang/String;Ljava/lang/String;)V":
							loginResponseHook(methodNode);
							break;
						case "(IBI)V":
							playerCoordHook(methodNode);
							playerDamagedHook(methodNode);
							npcDamagedHook(methodNode);
							deathHook(methodNode);
							groundItemSpawnedHook(methodNode);
							groundItemDespawnedHook(methodNode);
							objectSpawnedHook(methodNode);
							objectDespawnedHook(methodNode);
							sleepStartHook(methodNode);
							sleepFatigueUpdateHook(methodNode);
							sleepWordIncorrectHook(methodNode);
							sleepStopHook(methodNode);
							cameraRefocusHook(methodNode);
							closeWelcomeBox(methodNode);
							npcUpdateHook(methodNode);
							unhandledOpCodeFix(methodNode);
							break;
						case "(ZI)V":
							throwableCrashFix(methodNode);
							break;
						case "(Z)V":
							safeify(methodNode);
							break;
						default:
							break;
					}
					break;
				case "e":
					if (methodNode.desc.equals("(I)V"))
					{
						cameraUpdateHook(methodNode);
						safeify(methodNode);
					}
					break;
				case "f":
					if (methodNode.desc.equals("(I)V"))
					{
						renderRoofsHook(methodNode);
						closeWildernessWarning(methodNode);
						// ordered
						renderGraphicsHook(methodNode);
						runScriptHook(methodNode);
					}
					break;
				case "I":
					if (methodNode.desc.equals("(I)V"))
					{
						combatStyleMenuHook(methodNode);
					}
					break;
				case "i":
					if (methodNode.desc.equals("(I)V"))
					{
						cameraInitHook(methodNode);
					}
					break;
				case "<init>":
					increaseNetworkBufferFix(methodNode);
					increaseActionBubbleBufferFix(methodNode);
					break;
				case "J":
					if (methodNode.desc.equals("(I)V"))
					{
						removeIdleLogout(methodNode);
					}
					break;
				case "L":
					if (methodNode.desc.equals("(I)V"))
					{
						menuFix(methodNode);
					}
					break;
				case "O":
					if (methodNode.desc.equals("(I)V"))
					{
						chatBoxFix(methodNode);
					}
					break;
				case "s":
					if (methodNode.desc.equals("(I)V"))
					{
						debugObjectHook(methodNode);
						debugWallHook(methodNode);
						debugGroundItemHook(methodNode);
						debugNpcHook(methodNode);
					}
					break;
				case "x":
					if (methodNode.desc.equals("(I)V"))
					{
						loginScreenHook(methodNode);
					}
					break;
				default:
					break;
			}
		}
	}

	private static void patchApplet(final ClassNode classNode)
	{
		log.info("Patching class: applet");

		for (final MethodNode methodNode : classNode.methods)
		{
			switch (methodNode.name)
			{
				case "a":
					if (methodNode.desc.equals("(Ljava/lang/String;III)[B"))
					{
						loadContentHook(methodNode);
					}
					break;
				case "isDisplayable":
					if (methodNode.desc.equals("()Z"))
					{
						isDisplayableFix(methodNode);
					}
					break;
				case "mousePressed":
				case "mouseDragged":
					if (methodNode.desc.equals("(Ljava/awt/event/MouseEvent;)V"))
					{
						rightClickFix(methodNode);
					}
					break;
				case "run":
					if (methodNode.desc.equals("()V"))
					{
						throwableCrashFix(methodNode);
						mouseAndKeyListenerHooks(methodNode);
					}
					break;
				case "keyPressed":
					methodNode.access &= ~Opcodes.ACC_FINAL;
					break;
				default:
					break;
			}
		}
	}

	private static void patchClass_gb(final ClassNode classNode)
	{
		log.info("Patching class: gb");

		for (final MethodNode methodNode : classNode.methods)
		{
			if (methodNode.name.equals("a") && methodNode.desc.equals("(IIBIII[IIIIII[III)V"))
			{
				renderTexturesHook(methodNode);
				break;
			}
		}
	}

	private static void patchRendererHelper(final ClassNode classNode)
	{
		log.info("Patching class: rendererHelper");

		for (final MethodNode methodNode : classNode.methods)
		{
			if (methodNode.name.equals("c") && methodNode.desc.equals("(I)V"))
			{
				rendererHelperCrashFix(methodNode);
				break;
			}
		}
	}

	private static void patchClass_p(final ClassNode classNode)
	{
		log.info("Patching class: p");

		for (final MethodNode methodNode : classNode.methods)
		{
			if (methodNode.name.equals("a") && methodNode.desc.equals("(IIIII[IIIII[IIIII)V"))
			{
				renderTexturesHook(methodNode);
				break;
			}
		}
	}

	private static void patchClass_t(final ClassNode classNode)
	{
		log.info("Patching class: t");

		for (final MethodNode methodNode : classNode.methods)
		{
			if (methodNode.name.equals("a") && methodNode.desc.equals("(III[I[IIII)V"))
			{
				renderSolidHook(methodNode);
				break;
			}
		}
	}

	private static void patchRenderer(final ClassNode classNode)
	{
		log.info("Patching class: renderer");

		for (final MethodNode methodNode : classNode.methods)
		{
			if (methodNode.name.equals("a") && methodNode.desc.equals("(B[BI)V"))
			{
				sleepWordHook(methodNode);
				break;
			}
		}
	}

	private static void patchClass_wb(final ClassNode classNode)
	{
		log.info("Patching class: wb");

		for (final MethodNode methodNode : classNode.methods)
		{
			if (methodNode.name.equals("a") && methodNode.desc.equals("(IIII[IIIIIIIIII[II)V"))
			{
				renderTexturesHook(methodNode);
				break;
			}
		}
	}

	private static void loginScreenHook(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"LoginListener", "loginScreenHook", "()V"));

	}

	private static void loginResponseHook(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"LoginListener", "loginResponseHook", "(Ljava/lang/String;Ljava/lang/String;)V"));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 3));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 2));

	}

	private static void removeIdleLogout(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.INVOKESPECIAL)
			{
				final MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;

				if (methodInsnNode.owner.equals("client") && methodInsnNode.name.equals("B") &&
					methodInsnNode.desc.equals("(I)V"))
				{
					methodNode.instructions.insertBefore(methodInsnNode, new InsnNode(Opcodes.POP2));
					methodNode.instructions.remove(methodInsnNode);
					break;
				}
			}
		}

	}

	private static void menuFix(final MethodNode methodNode)
	{
		final InsnList insnList = methodNode.instructions;

		int size = insnList.size();

		for (int i = 0; i < size; ++i)
		{
			AbstractInsnNode insn = insnList.get(i);

			if (!(insn instanceof IntInsnNode))
			{
				continue;
			}

			IntInsnNode push = (IntInsnNode) insn;
			InsnList list;

			switch (push.operand)
			{
				case 510:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Wd", "I"));
					list.add(new InsnNode(Opcodes.ICONST_2));
					list.add(new InsnNode(Opcodes.ISUB));
					insnList.insertBefore(push, list);
					insnList.remove(push);
					i = 0;
					break;
				case ~510:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Wd", "I"));
					list.add(new InsnNode(Opcodes.ICONST_2));
					list.add(new InsnNode(Opcodes.ISUB));
					list.add(new InsnNode(Opcodes.ICONST_M1));
					list.add(new InsnNode(Opcodes.IXOR));
					insnList.insertBefore(push, list);
					insnList.remove(push);
					i = 0;
					break;
				case 315:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Oi", "I"));
					list.add(new IntInsnNode(Opcodes.BIPUSH, 19));
					list.add(new InsnNode(Opcodes.ISUB));
					insnList.insertBefore(push, list);
					insnList.remove(push);
					i = 0;
					break;
				case ~315:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Oi", "I"));
					list.add(new IntInsnNode(Opcodes.BIPUSH, 19));
					list.add(new InsnNode(Opcodes.ISUB));
					list.add(new InsnNode(Opcodes.ICONST_M1));
					list.add(new InsnNode(Opcodes.IXOR));
					insnList.insertBefore(push, list);
					insnList.remove(push);
					i = 0;
					break;
			}
		}
	}

	private static void chatBoxFix(final MethodNode methodNode)
	{
		final InsnList insnList = methodNode.instructions;

		final int[] values = new int[]{269, 502, 324, 498, 269, 502, 269, 502};

		final IntInsnNode[] found = new IntInsnNode[values.length];

		for (int i = 0; i < insnList.size(); ++i)
		{
			AbstractInsnNode insn = insnList.get(i);

			if (!(insn instanceof IntInsnNode))
			{
				continue;
			}

			IntInsnNode n = (IntInsnNode) insn;

			for (int j = 0; j < values.length; ++j)
			{
				if (values[j] == n.operand &&
					found[j] == null)
				{
					found[j] = n;
					break;
				}
			}
		}

		for (AbstractInsnNode insn : found)
		{
			if (insn == null)
			{
				throw new RuntimeException("Failed to fix chatbox");
			}
		}

		for (int i = 0; i < found.length; ++i)
		{
			InsnList list = new InsnList();

			list.add(new VarInsnNode(Opcodes.ALOAD, 0));

			if (((i + 1) % 2) == 0)
			{
				list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Wd", "I"));
				list.add(new IntInsnNode(Opcodes.SIPUSH, 512));
				//found[i].operand = w - (512 - values[i]);
			}
			else
			{
				list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Oi", "I"));
				list.add(new IntInsnNode(Opcodes.BIPUSH, 12));
				list.add(new InsnNode(Opcodes.IADD));
				list.add(new IntInsnNode(Opcodes.SIPUSH, 346));
				//found[i].operand = h - (346 - values[i]);
			}

			insnList.insertBefore(found[i], list);

			list = new InsnList();
			list.add(new InsnNode(Opcodes.ISUB));
			list.add(new InsnNode(Opcodes.ISUB));
			insnList.insert(found[i], list);
		}
	}

	private static void closeWildernessWarning(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("le") &&
					fieldInsnNode.desc.equals("I") && fieldInsnNode.getPrevious().getOpcode() == Opcodes.ICONST_1)
				{
					methodNode.instructions.remove(fieldInsnNode.getPrevious());
					methodNode.instructions.insertBefore(fieldInsnNode, new InsnNode(Opcodes.ICONST_2));
					break;
				}
			}
		}

	}

	private static void closeWelcomeBox(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("Oh") &&
					fieldInsnNode.desc.equals("Z") && fieldInsnNode.getPrevious().getOpcode() == Opcodes.ICONST_1)
				{
					methodNode.instructions.remove(fieldInsnNode.getPrevious());
					methodNode.instructions.insertBefore(fieldInsnNode, new InsnNode(Opcodes.ICONST_0));
					break;
				}
			}
		}

	}

	private static void debugObjectHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) abstractInsnNode).operand == 3400 &&
				abstractInsnNode.getNext().getOpcode() == Opcodes.ICONST_0)
			{

				AbstractInsnNode node = abstractInsnNode;

				do
				{
					node = node.getNext();
				} while (node.getOpcode() != Opcodes.INVOKEVIRTUAL || !(((MethodInsnNode) node).name.equals("toString")));

				final LabelNode labelNode = new LabelNode();

				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "debug", "Z"));
				methodNode.instructions.insertBefore(node, new JumpInsnNode(Opcodes.IFEQ, labelNode));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(" " + DEBUG_COLOR + "("));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 10));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(","));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Se", "[I"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IALOAD));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Qg", "I"));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IADD));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(","));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "ye", "[I"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IALOAD));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "zg", "I"));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IADD));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(")"));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, labelNode);
				break;
			}
		}

	}

	private static void debugWallHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) abstractInsnNode).operand == 3300)
			{
				AbstractInsnNode node = abstractInsnNode;

				do
				{
					node = node.getNext();
				} while (node.getOpcode() != Opcodes.INVOKEVIRTUAL || !(((MethodInsnNode) node).name.equals("toString")));

				final LabelNode labelNode = new LabelNode();

				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "debug", "Z"));
				methodNode.instructions.insertBefore(node, new JumpInsnNode(Opcodes.IFEQ, labelNode));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(" " + DEBUG_COLOR + "("));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 10));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(","));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Jd", "[I"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IALOAD));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Qg", "I"));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IADD));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(","));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "yk", "[I"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IALOAD));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "zg", "I"));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IADD));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(")"));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, labelNode);
				break;
			}
		}

	}

	private static void debugGroundItemHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) abstractInsnNode).operand == 3200)
			{
				AbstractInsnNode node = abstractInsnNode;

				do
				{
					node = node.getNext();
				} while (node.getOpcode() != Opcodes.INVOKEVIRTUAL || !(((MethodInsnNode) node).name.equals("toString")));

				final LabelNode labelNode = new LabelNode();

				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "debug", "Z"));
				methodNode.instructions.insertBefore(node, new JumpInsnNode(Opcodes.IFEQ, labelNode));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(" " + DEBUG_COLOR + "("));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Gj", "[I"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IALOAD));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(","));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Zf", "[I"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IALOAD));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Qg", "I"));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IADD));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(","));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Ni", "[I"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IALOAD));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "zg", "I"));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.IADD));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(")"));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, labelNode);
				break;
			}
		}

	}

	private static void debugNpcHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) abstractInsnNode).operand == 3700)
			{
				AbstractInsnNode node = abstractInsnNode;

				do
				{
					node = node.getNext();
				} while (node.getOpcode() != Opcodes.INVOKEVIRTUAL || !(((MethodInsnNode) node).name.equals("toString")));

				final LabelNode labelNode = new LabelNode();

				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "debug", "Z"));
				methodNode.instructions.insertBefore(node, new JumpInsnNode(Opcodes.IFEQ, labelNode));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(" " + DEBUG_COLOR + "("));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "Tb", "[Lta;"));
				methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ILOAD, 9));
				methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.AALOAD));
				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETFIELD,
					"ta", "t", "I"));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(")"));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, labelNode);
				break;
			}
		}

	}

	private static void debugInventoryItemHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) abstractInsnNode).operand == 3600)
			{
				AbstractInsnNode node = abstractInsnNode;

				do
				{
					node = node.getNext();
				} while (node.getOpcode() != Opcodes.INVOKEVIRTUAL || !(((MethodInsnNode) node).name.equals("toString")));

				final LabelNode labelNode = new LabelNode();

				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "debug", "Z"));
				methodNode.instructions.insertBefore(node, new JumpInsnNode(Opcodes.IFEQ, labelNode));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(" " + DEBUG_COLOR + "("));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new IntInsnNode(Opcodes.ILOAD, 6));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, new LdcInsnNode(")"));
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
				methodNode.instructions.insertBefore(node, labelNode);
				break;
			}
		}

	}

	private static void showReportPlayerHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) abstractInsnNode).operand == 2820 &&
				abstractInsnNode.getNext().getOpcode() == Opcodes.ICONST_0)
			{
				AbstractInsnNode node = abstractInsnNode;

				do
				{
					node = node.getNext();
				} while (node.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) node).var != 0);

				AbstractInsnNode endNode = abstractInsnNode;

				do
				{
					endNode = endNode.getNext();
				} while (endNode.getOpcode() != Opcodes.GOTO);

				final LabelNode labelNode = ((JumpInsnNode) endNode).label;

				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "showReportPlayer", "Z"));
				methodNode.instructions.insertBefore(node, new JumpInsnNode(Opcodes.IFEQ, labelNode));
				break;
			}
		}

	}

	private static void combatStyleMenuHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) abstractInsnNode).operand == 9)
			{
				AbstractInsnNode node = abstractInsnNode;
				while (node.getOpcode() != Opcodes.ALOAD)
				{
					node = node.getNext();
				}

				LabelNode label = new LabelNode();
				final LabelNode skipLabel = new LabelNode();

				methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "hideCombatStyleMenu", "Z"));
				methodNode.instructions.insertBefore(node, new JumpInsnNode(Opcodes.IFNE, label));
				methodNode.instructions.insertBefore(node, skipLabel);
				methodNode.instructions.insert(node.getNext().getNext(), label);

				final JumpInsnNode jumpNode = (JumpInsnNode) abstractInsnNode.getNext();
				final LabelNode exitLabel = jumpNode.label;
				final LabelNode runLabel = (LabelNode) jumpNode.getNext();
				label = new LabelNode();
				jumpNode.label = label;

				methodNode.instructions.insert(jumpNode, new JumpInsnNode(Opcodes.GOTO, exitLabel));
				methodNode.instructions.insert(jumpNode, new JumpInsnNode(Opcodes.IFNE, skipLabel));
				methodNode.instructions.insert(jumpNode, new FieldInsnNode(Opcodes.GETSTATIC,
					"CommandListener", "showCombatStyleMenu", "Z"));
				methodNode.instructions.insert(jumpNode, label);
				methodNode.instructions.insert(jumpNode, new JumpInsnNode(Opcodes.GOTO, runLabel));
				break;
			}
		}

	}

	private static void renderRoofsHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() != Opcodes.BIPUSH || ((IntInsnNode) abstractInsnNode).operand != 118)
			{
				continue;
			}

			final LabelNode labelNode = ((JumpInsnNode) abstractInsnNode
				.getNext() // INVOKEVIRTUAL jagex/lb.a (Ljagex/ca;B)V
				.getNext() // -1
				.getNext() // -1
				.getNext() // ICONST_0
				.getNext() // ALOAD 0
				.getNext() // GETFIELD jagex/client.yj : I
				.getNext() // IF_ICMPEQ L208
				.getNext() // -1
				.getNext() // GOTO L209
			).label;

			final AbstractInsnNode startNode = abstractInsnNode
				.getPrevious() // AALOAD
				.getPrevious() // ILOAD 2
				.getPrevious() // AALOAD
				.getPrevious() // GETFIELD jagex/client.yj : I
				.getPrevious() // ALOAD 0
				.getPrevious() // GETFIELD jagex/k.db : [[Ljagex/ca;
				.getPrevious() // GETFIELD jagex/client.Hh : Ljagex/k;
				.getPrevious() // ALOAD 0
				.getPrevious() // GETFIELD jagex/client.Ek : Ljagex/lb;
				.getPrevious(); // ALOAD 0

			methodNode.instructions.insertBefore(startNode, new FieldInsnNode(Opcodes.GETSTATIC,
				"CommandListener", "renderRoofs", "Z"));
			methodNode.instructions.insertBefore(startNode, new JumpInsnNode(Opcodes.IFEQ, labelNode));
			break;
		}

	}

	private static void playerCoordHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("wi") &&
					fieldInsnNode.desc.equals("Lta;"))
				{
					methodNode.instructions.insert(fieldInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"ScriptListener", "playerCoordHook", "(II)V"));
					methodNode.instructions.insert(fieldInsnNode, new FieldInsnNode(Opcodes.GETFIELD,
						"client", "sh", "I"));
					methodNode.instructions.insert(fieldInsnNode, new VarInsnNode(Opcodes.ALOAD, 0));
					methodNode.instructions.insert(fieldInsnNode, new FieldInsnNode(Opcodes.GETFIELD,
						"client", "Lf", "I"));
					methodNode.instructions.insert(fieldInsnNode, new VarInsnNode(Opcodes.ALOAD, 0));
					break;
				}
			}
		}

	}

	private static void playerDamagedHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) abstractInsnNode).operand == 200)
			{
				methodNode.instructions.insertBefore(abstractInsnNode, new InsnNode(Opcodes.DUP));
				methodNode.instructions.insert(abstractInsnNode.getNext(), // PUTFIELD ta.d : I
					new MethodInsnNode(Opcodes.INVOKESTATIC, "ScriptListener",
						"playerDamagedHook", "(Lta;)V"));
				break;
			}
		}

	}

	private static void npcDamagedHook(final MethodNode methodNode)
	{
		boolean skip = true;

		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("ta") && fieldInsnNode.name.equals("B") &&
					fieldInsnNode.desc.equals("I"))
				{
					if (skip)
					{
						skip = false;
						continue;
					}

					methodNode.instructions.insert(fieldInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"ScriptListener", "npcDamagedHook", "(Lta;)V"));
					methodNode.instructions.insert(fieldInsnNode, new VarInsnNode(Opcodes.ALOAD, 7));
					break;
				}
			}
		}

	}

	private static void deathHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("rk") &&
					fieldInsnNode.desc.equals("I"))
				{
					methodNode.instructions.insert(fieldInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"ScriptListener", "deathHook", "()V"));
					break;
				}
			}
		}

	}

	private static void groundItemSpawnedHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.GETFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("Ah") &&
					fieldInsnNode.desc.equals("I") && fieldInsnNode.getNext().getOpcode() == Opcodes.ICONST_0)
				{
					final AbstractInsnNode nextNode = fieldInsnNode
						.getNext() // ICONST_0
						.getNext(); // IASTORE

					methodNode.instructions.insert(nextNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"ScriptListener", "groundItemSpawnedHook", "(I)V"));
					methodNode.instructions.insert(nextNode, new FieldInsnNode(Opcodes.GETFIELD,
						"client", "Ah", "I"));
					methodNode.instructions.insert(nextNode, new VarInsnNode(Opcodes.ALOAD, 0));
					break;
				}
			}
		}

	}

	private static void groundItemDespawnedHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) abstractInsnNode).operand == -123)
			{
				methodNode.instructions.insertBefore(abstractInsnNode, new VarInsnNode(Opcodes.ILOAD, 8));
				methodNode.instructions.insertBefore(abstractInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
					"ScriptListener", "groundItemDespawnedHook", "(I)V"));
				break;
			}
		}

	}

	private static void objectSpawnedHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) abstractInsnNode).var == 8 &&
				abstractInsnNode.getNext().getOpcode() == Opcodes.IASTORE)
			{
				final AbstractInsnNode node = abstractInsnNode.getNext();
				methodNode.instructions.insert(node, new MethodInsnNode(Opcodes.INVOKESTATIC,
					"ScriptListener", "objectSpawnedHook", "(I)V"));
				methodNode.instructions.insert(node, new FieldInsnNode(Opcodes.GETFIELD,
					"client", "eh", "I"));
				methodNode.instructions.insert(node, new VarInsnNode(Opcodes.ALOAD, 0));
				break;
			}
		}

	}

	private static void objectDespawnedHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.INVOKEVIRTUAL)
			{
				final MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;

				if (methodInsnNode.owner.equals("k") && methodInsnNode.name.equals("a") &&
					methodInsnNode.desc.equals("(IIII)V"))
				{
					methodNode.instructions.insert(abstractInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"ScriptListener", "objectDespawnedHook", "(I)V"));
					methodNode.instructions.insert(abstractInsnNode, new VarInsnNode(Opcodes.ILOAD, 8));
					break;
				}
			}
		}

	}

	private static void initHook(final MethodNode methodNode)
	{
		methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), new MethodInsnNode(Opcodes.INVOKESTATIC,
			"Extension", "initHook", "()V"));

	}

	private static void rsaFieldsHook(final MethodNode methodNode)
	{
		hookStaticVariable(methodNode,
			"s", "c", "Ljava/math/BigInteger;",
			"Extension", "exponent", "Ljava/math/BigInteger;");
		hookStaticVariable(methodNode,
			"ja", "K", "Ljava/math/BigInteger;",
			"Extension", "modulus", "Ljava/math/BigInteger;");
	}

	private static void renderGraphicsHook(final MethodNode methodNode)
	{
		final LabelNode labelNode = new LabelNode();
		methodNode.instructions.insert(labelNode);
		methodNode.instructions.insert(new InsnNode(Opcodes.RETURN));
		methodNode.instructions.insert(new JumpInsnNode(Opcodes.IFNE, labelNode));
		methodNode.instructions.insert(new FieldInsnNode(Opcodes.GETSTATIC,
			"PaintListener", "renderGraphics", "Z"));

	}

	private static void renderTexturesHook(final MethodNode methodNode)
	{
		final LabelNode labelNode = new LabelNode();
		methodNode.instructions.insert(labelNode);
		methodNode.instructions.insert(new InsnNode(Opcodes.RETURN));
		methodNode.instructions.insert(new JumpInsnNode(Opcodes.IFNE, labelNode));
		methodNode.instructions.insert(new FieldInsnNode(Opcodes.GETSTATIC,
			"PaintListener", "renderTextures", "Z"));

	}

	private static void renderSolidHook(final MethodNode methodNode)
	{
		final LabelNode labelNode = new LabelNode();
		methodNode.instructions.insert(labelNode);
		methodNode.instructions.insert(new InsnNode(Opcodes.RETURN));
		methodNode.instructions.insert(new JumpInsnNode(Opcodes.IFNE, labelNode));
		methodNode.instructions.insert(new FieldInsnNode(Opcodes.GETSTATIC,
			"PaintListener", "renderSolid", "Z"));

	}

	private static void loadContentCrcsHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.INVOKESTATIC)
			{
				final MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;

				if (methodInsnNode.owner.equals("da") && methodInsnNode.name.equals("a") &&
					methodInsnNode.desc.equals("(Ljava/net/URL;ZZ)[B"))
				{
					methodInsnNode.owner = "StaticAccess";
					methodInsnNode.name = "loadContentCrcsHook";
					break;
				}
			}
		}

	}

	private static void loadContentHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.INVOKESTATIC)
			{
				final MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;

				if (methodInsnNode.owner.equals("ib") && methodInsnNode.name.equals("a") &&
					methodInsnNode.desc.equals("(ILjava/lang/String;II)[B"))
				{
					methodInsnNode.owner = "StaticAccess";
					methodInsnNode.name = "loadContentHook";
					break;
				}
			}
		}

	}

	private static void runScriptHook(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"ScriptListener", "runScriptHook", "()V"));

	}

	private static void commandHook(final MethodNode methodNode)
	{
		final LabelNode labelNode = new LabelNode();
		methodNode.instructions.insert(labelNode);
		methodNode.instructions.insert(new InsnNode(Opcodes.RETURN));
		methodNode.instructions.insert(new JumpInsnNode(Opcodes.IFNE, labelNode));
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"CommandListener", "commandHook", "(Ljava/lang/String;)Z"));
		methodNode.instructions.insert(new IntInsnNode(Opcodes.ALOAD, 1));

	}

	private static void paintHook(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"PaintListener", "paintHook", "()V"));

	}

	private static void removeURLCheck(final MethodNode methodNode)
	{
		for (final AbstractInsnNode insnNode : methodNode.instructions)
		{
			if (insnNode.getOpcode() == Opcodes.IFEQ)
			{
				final JumpInsnNode jumpInsnNode = (JumpInsnNode) insnNode;
				methodNode.instructions.insert(insnNode, new JumpInsnNode(Opcodes.GOTO, jumpInsnNode.label));
				methodNode.instructions.remove(jumpInsnNode.getPrevious());
				methodNode.instructions.remove(jumpInsnNode.getPrevious());
				methodNode.instructions.remove(jumpInsnNode);
				break;
			}
		}

	}

	private static void gameMessageHook(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"ScriptListener", "gameMessageHook",
			"(ZLjava/lang/String;ILjava/lang/String;IILjava/lang/String;Ljava/lang/String;)V"));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 8));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 7));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ILOAD, 6));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ILOAD, 5));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 4));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ILOAD, 3));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 2));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ILOAD, 1));

	}

	private static void npcSpawnedHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("ta") && fieldInsnNode.name.equals("K") &&
					fieldInsnNode.desc.equals("I"))
				{
					final AbstractInsnNode nextNode = fieldInsnNode.getNext(); // IASTORE

					methodNode.instructions.insert(nextNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"ScriptListener", "npcSpawnedHook", "(Lta;)V"));
					methodNode.instructions.insert(nextNode, new VarInsnNode(Opcodes.ALOAD, 7));
					break;
				}
			}
		}

	}

	private static void npcUpdateHook(final MethodNode methodNode)
	{
		boolean skip = true;
		for (final AbstractInsnNode node : methodNode.instructions)
		{
			if (node.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) node).operand == 25505)
			{
				if (skip)
				{
					skip = false;
					continue;
				}

				methodNode.instructions.insert(node.getNext(), new MethodInsnNode(Opcodes.INVOKESTATIC,
					"ScriptListener", "npcUpdateHook", "()V"));
				break;
			}
		}

	}

	private static void isDisplayableFix(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new InsnNode(Opcodes.IRETURN));
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESPECIAL,
			"java/applet/Applet", "isDisplayable", "()Z", false));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 0));

	}

	private static void rightClickFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode node : methodNode.instructions)
		{
			if (node.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) node).name.equals("isMetaDown"))
			{
				methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKESTATIC,
					"javax/swing/SwingUtilities", "isRightMouseButton",
					"(Ljava/awt/event/MouseEvent;)Z"));
				methodNode.instructions.remove(node);
				break;
			}
		}

	}

	private static void sleepWordHook(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"SleepListener", "sleepWordHook", "([B)V"));
		methodNode.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 2));

	}

	private static void sleepStartHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("Zj") &&
					fieldInsnNode.desc.equals("Ljava/lang/String;") &&
					fieldInsnNode.getPrevious().getOpcode() == Opcodes.ACONST_NULL)
				{
					methodNode.instructions.insert(fieldInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"SleepListener", "sleepStartHook", "()V"));
					break;
				}
			}
		}

	}

	private static void sleepFatigueUpdateHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("pg") &&
					fieldInsnNode.desc.equals("I") &&
					fieldInsnNode.getPrevious().getOpcode() == Opcodes.INVOKEVIRTUAL)
				{
					methodNode.instructions.insert(fieldInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"SleepListener", "sleepFatigueUpdateHook", "(I)V"));
					methodNode.instructions.insert(fieldInsnNode, new FieldInsnNode(Opcodes.GETFIELD,
						"client", "pg", "I"));
					methodNode.instructions.insert(fieldInsnNode, new VarInsnNode(Opcodes.ALOAD, 0));
					break;
				}
			}
		}

	}

	private static void sleepWordIncorrectHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("Zj") &&
					fieldInsnNode.desc.equals("Ljava/lang/String;") &&
					fieldInsnNode.getPrevious().getOpcode() == Opcodes.LDC)
				{
					methodNode.instructions.insert(fieldInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"SleepListener", "sleepWordIncorrectHook", "()V"));
					break;
				}
			}
		}

	}

	private static void sleepStopHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals("client") && fieldInsnNode.name.equals("Qk") &&
					fieldInsnNode.desc.equals("Z") &&
					fieldInsnNode.getPrevious().getOpcode() == Opcodes.ICONST_0)
				{
					methodNode.instructions.insert(fieldInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
						"SleepListener", "sleepStopHook", "()V"));
					break;
				}
			}
		}

	}

	private static void safeify(final MethodNode methodNode)
	{
		final LabelNode labelNodeStart = new LabelNode();
		final LabelNode labelNodeEnd = new LabelNode();

		methodNode.instructions.insert(labelNodeStart);
		methodNode.instructions.add(labelNodeEnd);

		final LabelNode labelNodeHandler = new LabelNode();

		final InsnList insnList = new InsnList();
		insnList.add(labelNodeHandler);
		insnList.add(new InsnNode(Opcodes.RETURN));

		methodNode.instructions.add(insnList);

		final TryCatchBlockNode tryCatchBlockNode = new TryCatchBlockNode(
			labelNodeStart,
			labelNodeEnd,
			labelNodeHandler,
			"java/lang/RuntimeException"
		);

		methodNode.tryCatchBlocks.add(tryCatchBlockNode);
	}

	private static void rendererHelperCrashFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() != Opcodes.INVOKESTATIC) continue;

			final AbstractInsnNode nextNode = abstractInsnNode.getNext();

			if (nextNode == null || nextNode.getOpcode() != Opcodes.ATHROW) continue;

			methodNode.instructions.insertBefore(nextNode, new InsnNode(Opcodes.RETURN));
			methodNode.instructions.remove(nextNode);
			break;
		}

	}

	private static void throwableCrashFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.INVOKESTATIC)
			{
				final AbstractInsnNode nextNode = abstractInsnNode.getNext();

				if (nextNode == null) break;

				if (nextNode.getOpcode() == Opcodes.ATHROW)
				{
					methodNode.instructions.insert(abstractInsnNode, new InsnNode(Opcodes.RETURN));
					break;
				}
			}
		}

	}

	private static void unhandledOpCodeFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.ACONST_NULL &&
				abstractInsnNode.getPrevious().getOpcode() == Opcodes.LDC)
			{
				methodNode.instructions.insertBefore(abstractInsnNode.getPrevious(), new InsnNode(Opcodes.RETURN));
				break;
			}
		}

	}

	private static void increaseNetworkBufferFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH)
			{
				final IntInsnNode intInsnNode = (IntInsnNode) abstractInsnNode;

				if (intInsnNode.operand == 5000)
				{
					intInsnNode.operand = 6000;
					break;
				}
			}
		}

	}

	private static void increaseActionBubbleBufferFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() != Opcodes.PUTFIELD)
			{
				continue;
			}

			final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

			if (!fieldInsnNode.owner.equals("client") || !fieldInsnNode.desc.equals("[I"))
			{
				continue;
			}

			final String name = fieldInsnNode.name;

			if (!name.equals("je") && !name.equals("pe") && !name.equals("jd") && !name.equals("ak"))
			{
				continue;
			}

			final AbstractInsnNode node = fieldInsnNode.getPrevious().getPrevious();

			if (node.getOpcode() != Opcodes.BIPUSH)
			{
				continue;
			}

			final IntInsnNode intInsnNode = (IntInsnNode) node;

			if (intInsnNode.operand != 50)
			{
				continue;
			}

			intInsnNode.setOpcode(Opcodes.SIPUSH);
			intInsnNode.operand = 255;
		}

	}

	private static void mouseAndKeyListenerHooks(final MethodNode methodNode)
	{
		AbstractInsnNode node = methodNode.instructions.getFirst();

		while (true)
		{
			final AbstractInsnNode nextNode = node.getNext();

			if (nextNode == null) break;

			if (node.getOpcode() == Opcodes.ALOAD && nextNode.getOpcode() == Opcodes.ALOAD)
			{
				final MethodInsnNode methodInsnNode = (MethodInsnNode) nextNode.getNext(); // INVOKEVIRTUAL

				methodNode.instructions.remove(nextNode);
				methodNode.instructions.remove(methodInsnNode);

				switch (methodInsnNode.name)
				{
					case "addMouseListener":
						methodNode.instructions.insert(node, new FieldInsnNode(Opcodes.PUTSTATIC,
							"com/aposbot/handler/MouseHandler", "mouseListener",
							"Ljava/awt/event/MouseListener;"));
						break;
					case "addMouseMotionListener":
						methodNode.instructions.insert(node, new FieldInsnNode(Opcodes.PUTSTATIC,
							"com/aposbot/handler/MouseHandler", "mouseMotionListener",
							"Ljava/awt/event/MouseMotionListener;"));
						break;
					case "addKeyListener":
						methodNode.instructions.insert(node, new FieldInsnNode(Opcodes.PUTSTATIC,
							"com/aposbot/handler/KeyboardHandler", "keyListener",
							"Ljava/awt/event/KeyListener;"));
						break;
				}
			}

			node = node.getNext();
		}

	}

	private static void cameraViewDistanceCrashFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.SIPUSH)
			{
				final IntInsnNode intInsnNode = (IntInsnNode) abstractInsnNode;

				if (intInsnNode.operand == 15000)
				{
					intInsnNode.operand = 32767;
				}
			}
		}

	}

	private static void cameraInitHook(final MethodNode methodNode)
	{
		methodNode.instructions.insertBefore(methodNode.instructions.getLast(), new MethodInsnNode(Opcodes.INVOKESTATIC,
			"com/aposbot/handler/CameraHandler", "initHook", "()V"));

	}

	private static void cameraRefocusHook(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() != Opcodes.ILOAD ||
				((VarInsnNode) abstractInsnNode).var != 5 ||
				abstractInsnNode.getNext().getOpcode() != Opcodes.IFNE ||
				abstractInsnNode.getNext().getNext().getOpcode() != Opcodes.GOTO)
			{
				continue;
			}

			methodNode.instructions.insertBefore(abstractInsnNode, new VarInsnNode(Opcodes.ILOAD, 5));
			methodNode.instructions.insertBefore(abstractInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC,
				"com/aposbot/handler/CameraHandler", "refocusHook", "(Z)V"));
			break;
		}

	}

	private static void cameraUpdateHook(final MethodNode methodNode)
	{
		methodNode.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
			"com/aposbot/handler/CameraHandler", "updateHook", "()V"));

	}

	private static void cameraFieldsHook(final MethodNode methodNode)
	{
		final String newClass = "com/aposbot/handler/CameraHandler";

		hookClassVariable(methodNode,
			"client", "ac", "I",
			newClass, "zoom", "I",
			false, true);
		hookClassVariable(methodNode,
			"client", "ug", "I",
			newClass, "rotation", "I",
			false, true);
		hookClassVariable(methodNode,
			"client", "Be", "I",
			newClass, "rotationY", "I",
			true, true);
		hookClassVariable(methodNode,
			"client", "kg", "I",
			newClass, "lookAtX", "I",
			false, true);
		hookClassVariable(methodNode,
			"client", "Si", "I",
			newClass, "lookAtY", "I",
			false, true);
		hookClassVariable(methodNode,
			"client", "Kh", "Z",
			newClass, "auto", "Z",
			true, true);
		hookClassVariable(methodNode,
			"client", "si", "I",
			newClass, "angle", "I",
			true, true);
		hookConditionalClassVariable(methodNode,
			"client", "qd", "I",
			newClass, "fov", "I",
			false, true, "fieldOfView");
		hookConditionalClassVariable(methodNode,
			"lb", "Mb", "I",
			newClass, "distance1", "I",
			false, true, "viewDistance");
		hookConditionalClassVariable(methodNode,
			"lb", "X", "I",
			newClass, "distance2", "I",
			false, true, "viewDistance");
		hookConditionalClassVariable(methodNode,
			"lb", "P", "I",
			newClass, "distance3", "I",
			false, true, "viewDistance");
		hookConditionalClassVariable(methodNode,
			"lb", "G", "I",
			newClass, "distance4", "I",
			false, true, "viewDistance");
	}

	private static void closeDuelAndTradeFix(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.INVOKESPECIAL)
			{
				final MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;

				if (methodInsnNode.owner.equals("client") &&
					methodInsnNode.name.equals("o") &&
					methodInsnNode.desc.equals("(I)V") &&
					methodInsnNode.getPrevious().getOpcode() == Opcodes.IXOR)
				{
					final AbstractInsnNode node = methodInsnNode.getPrevious() // IXOR
						.getPrevious() // BIPUSH -31
						.getPrevious() // ILOAD 2
						.getPrevious(); // ALOAD 0

					// Duel Accept Screen
					methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
					methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.ICONST_0));
					methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.PUTFIELD,
						"client", "Pj", "Z"));

					// Duel Confirm Screen
					methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
					methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.ICONST_0));
					methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.PUTFIELD,
						"client", "dd", "Z"));

					// Trade Accept Screen
					methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
					methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.ICONST_0));
					methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.PUTFIELD,
						"client", "Hk", "Z"));

					// Trade Confirm Screen
					methodNode.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 0));
					methodNode.instructions.insertBefore(node, new InsnNode(Opcodes.ICONST_0));
					methodNode.instructions.insertBefore(node, new FieldInsnNode(Opcodes.PUTFIELD,
						"client", "Xj", "Z"));
					break;
				}
			}
		}

	}

	private static void friendsListFix(final MethodNode methodNode)
	{
		final InsnList code = methodNode.instructions;

		int size = code.size();

		for (int i = 0; i < size; ++i)
		{
			AbstractInsnNode insn = code.get(i);

			if (!(insn instanceof IntInsnNode))
			{
				continue;
			}

			IntInsnNode push = (IntInsnNode) insn;
			InsnList list;

			switch (push.operand)
			{
				case 489:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Wd", "I"));
					list.add(new IntInsnNode(Opcodes.BIPUSH, 23));
					list.add(new InsnNode(Opcodes.ISUB));
					code.insertBefore(push, list);
					code.remove(push);
					i = 0;
					break;
				case ~489:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Wd", "I"));
					list.add(new IntInsnNode(Opcodes.BIPUSH, 23));
					list.add(new InsnNode(Opcodes.ISUB));
					list.add(new InsnNode(Opcodes.ICONST_M1));
					list.add(new InsnNode(Opcodes.IXOR));
					code.insertBefore(push, list);
					code.remove(push);
					i = 0;
					break;
				case 429:
				case 315:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Wd", "I"));
					list.add(new IntInsnNode(Opcodes.BIPUSH, 83));
					list.add(new InsnNode(Opcodes.ISUB));
					code.insertBefore(push, list);
					code.remove(push);
					i = 0;
					break;
				case ~429:
				case ~315:
					list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new FieldInsnNode(Opcodes.GETFIELD, "client", "Wd", "I"));
					list.add(new IntInsnNode(Opcodes.BIPUSH, 83));
					list.add(new InsnNode(Opcodes.ISUB));
					list.add(new InsnNode(Opcodes.ICONST_M1));
					list.add(new InsnNode(Opcodes.IXOR));
					code.insertBefore(push, list);
					code.remove(push);
					i = 0;
					break;
			}
		}
	}

	private static void friendsListRemove(final MethodNode methodNode)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() != Opcodes.LDC) continue;

			final LdcInsnNode ldcInsnNode = (LdcInsnNode) abstractInsnNode;

			if (!(ldcInsnNode.cst instanceof String)) continue;

			final String constant = (String) ldcInsnNode.cst;

			if (!constant.startsWith("~")) continue;

			methodNode.instructions.insertBefore(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC,
				"Extension", "friendsListRemove", "Ljava/lang/String;"));
			methodNode.instructions.remove(abstractInsnNode);
		}
	}

	private static void hookStaticVariable(final MethodNode methodNode,
		final String owner, final String name, final String desc,
		final String newOwner, final String newName, final String newDesc)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			if (abstractInsnNode.getOpcode() == Opcodes.GETSTATIC || abstractInsnNode.getOpcode() == Opcodes.PUTSTATIC)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals(owner) && fieldInsnNode.name.equals(name) && fieldInsnNode.desc.equals(desc))
				{
					fieldInsnNode.owner = newOwner;
					fieldInsnNode.name = newName;
					fieldInsnNode.desc = newDesc;
				}
			}
		}
	}

	private static void hookClassVariable(final MethodNode methodNode,
		final String owner, final String name, final String desc,
		final String newClass, final String newName, final String newDesc,
		final boolean canRead, final boolean canWrite)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			final int opcode = abstractInsnNode.getOpcode();

			if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals(owner) && fieldInsnNode.name.equals(name) &&
					fieldInsnNode.desc.equals(desc))
				{
					if (opcode == Opcodes.GETFIELD && canWrite)
					{
						methodNode.instructions.insert(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC,
							newClass, newName, newDesc));
						methodNode.instructions.insert(abstractInsnNode, new InsnNode(Opcodes.POP));
					}
					else if (opcode == Opcodes.PUTFIELD && canRead)
					{
						methodNode.instructions.insertBefore(abstractInsnNode, new InsnNode(Opcodes.DUP_X1));
						methodNode.instructions.insert(abstractInsnNode, new FieldInsnNode(Opcodes.PUTSTATIC,
							newClass, newName, newDesc));
					}
				}
			}
		}
	}

	private static void hookConditionalClassVariable(final MethodNode methodNode, final String owner, final String name,
		final String desc, final String newOwner, final String newName,
		final String newDesc, final boolean canRead, final boolean canWrite,
		final String boolName)
	{
		for (final AbstractInsnNode abstractInsnNode : methodNode.instructions)
		{
			final int opcode = abstractInsnNode.getOpcode();

			if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)
			{
				final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;

				if (fieldInsnNode.owner.equals(owner) && fieldInsnNode.name.equals(name) &&
					fieldInsnNode.desc.equals(desc))
				{
					if (opcode == Opcodes.GETFIELD && canWrite)
					{
						final LabelNode label = new LabelNode();

						methodNode.instructions.insert(abstractInsnNode, label);
						methodNode.instructions.insert(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC,
							newOwner, newName, newDesc));
						methodNode.instructions.insert(abstractInsnNode, new InsnNode(Opcodes.POP));
						methodNode.instructions.insert(abstractInsnNode, new JumpInsnNode(Opcodes.IFEQ, label));
						methodNode.instructions.insert(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC,
							newOwner, boolName, "Z"));
					}
					else if (opcode == Opcodes.PUTFIELD && canRead)
					{
						final LabelNode label = new LabelNode();
						final LabelNode endLabel = new LabelNode();

						methodNode.instructions.insertBefore(abstractInsnNode, new InsnNode(Opcodes.DUP_X1));
						methodNode.instructions.insert(abstractInsnNode, endLabel);
						methodNode.instructions.insert(abstractInsnNode, new InsnNode(Opcodes.POP));
						methodNode.instructions.insert(abstractInsnNode, label);
						methodNode.instructions.insert(abstractInsnNode, new JumpInsnNode(Opcodes.GOTO, endLabel));
						methodNode.instructions.insert(abstractInsnNode, new FieldInsnNode(Opcodes.PUTSTATIC,
							newOwner, newName, newDesc));
						methodNode.instructions.insert(abstractInsnNode, new JumpInsnNode(Opcodes.IFEQ, label));
						methodNode.instructions.insert(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC,
							newOwner, boolName, "Z"));
					}
				}
			}
		}
	}

}
