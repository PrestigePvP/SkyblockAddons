package codes.biscuit.skyblockaddons.tweaker;

import codes.biscuit.skyblockaddons.asm.*;
import codes.biscuit.skyblockaddons.tweaker.transformer.ITransformer;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class SkyblockAddonsTransformer implements IClassTransformer {

    private static boolean LABYMOD_CLIENT;
    private static boolean DEOBFUSCATED;

    static {
        DEOBFUSCATED = false;
        boolean foundLaunchClass = false;
        try {
            // DEOBFUSCATED = (boolean)Launch.blackboard.get("fml.deobfuscatedEnvironment");
            Class launch = Class.forName("net.minecraft.launchwrapper.Launch");
            Field blackboardField = launch.getField("blackboard");
            Map<String,Object> blackboard = (Map<String,Object>)blackboardField.get(null);
            DEOBFUSCATED = (boolean) blackboard.get("fml.deobfuscatedEnvironment");
            foundLaunchClass = true;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ex) {
            // If the class doesn't exist, its probably just obfuscated labymod client, so leave it false.
        }

        LABYMOD_CLIENT = false;
        try {
            Class.forName("net.labymod.api.LabyModAddon"); // Try to find a labymod class.
            LABYMOD_CLIENT = !foundLaunchClass; // If the launch class is also found, they are probably using labymod for forge and not the client.
        } catch (ClassNotFoundException ex) {
            // They just aren't using labymod.
        }
    }

    private static boolean USING_NOTCH_MAPPINGS = !DEOBFUSCATED;

    private final static boolean OUTPUT_BYTECODE = true;
    private Logger logger = LogManager.getLogger("SkyblockAddons Transformer");
    private final Multimap<String, ITransformer> transformerMap = ArrayListMultimap.create();

    public SkyblockAddonsTransformer() {
        registerTransformer(new TileEntityEnderChestRendererTransformer());
        registerTransformer(new MouseHelperTransformer());
        registerTransformer(new EntityPlayerSPTransformer());
        registerTransformer(new EntityRendererTransformer());
        registerTransformer(new SoundManagerTransformer());
        registerTransformer(new RenderManagerTransformer());
        registerTransformer(new PlayerControllerMPTransformer());
        registerTransformer(new NetHandlerPlayClientTransformer());
        registerTransformer(new MinecraftTransformer());
        registerTransformer(new ItemTransformer());
        registerTransformer(new GuiScreenTransformer());
        registerTransformer(new GuiContainerTransformer());
        registerTransformer(new GuiChestTransformer());
        registerTransformer(new GuiNewChatTransformer());
        registerTransformer(new RendererLivingEntityTransformer());
        registerTransformer(new GuiDisconnectedTransformer());
        registerTransformer(new GuiIngameMenuTransformer());
    }

    private void registerTransformer(ITransformer transformer) {
        for (String cls : transformer.getClassName()) {
            transformerMap.put(cls, transformer);
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;

        Collection<ITransformer> transformers = transformerMap.get(transformedName);
        if (transformers.isEmpty()) return bytes;

        logger.info("Found {} transformers for {}", transformers.size(), transformedName);

        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);

        transformers.forEach(transformer -> {
            logger.info("Applying transformer {} on {}...", transformer.getClass().getName(), transformedName);
            transformer.transform(node, transformedName);
        });

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        try {
            node.accept(writer);
        } catch (Throwable t) {
            logger.error("Exception when transforming " + transformedName + " : " + t.getClass().getSimpleName());
            t.printStackTrace();
            outputBytecode(transformedName, writer);
            return bytes;
        }

        outputBytecode(transformedName, writer);

        return writer.toByteArray();
    }

    private void outputBytecode(String transformedName, ClassWriter writer) {
        if (OUTPUT_BYTECODE) {
            try {
                File file = new File("C:\\Users\\jlroc\\Desktop\\bytecode", transformedName + ".class");
                if (file.getParentFile().exists()) {
                    file.createNewFile();
                    FileOutputStream os = new FileOutputStream(file);
                    os.write(writer.toByteArray());
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isDeobfuscated() {
        return DEOBFUSCATED;
    }

    public static boolean isLabymodClient() {
        return LABYMOD_CLIENT;
    }

    public static boolean isUsingNotchMappings() {
        return USING_NOTCH_MAPPINGS;
    }
}