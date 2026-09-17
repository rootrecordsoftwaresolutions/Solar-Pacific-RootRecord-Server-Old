package com.rootrecord.minecraft.rootavacore.schematic;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/** Writes Sponge Schematic Format v2 (.schem) as gzip-compressed NBT — no external NBT dep. */
public final class SpongeSchematicWriter {

    private SpongeSchematicWriter() {}

    public static byte[] write(ClaimSchematicCapture.CaptureResult capture, String schematicName)
            throws IOException {
        ByteArrayOutputStream gzipBuf = new ByteArrayOutputStream(Math.max(4096, capture.blockIndices().length));
        try (GZIPOutputStream gzip = new GZIPOutputStream(gzipBuf);
                DataOutputStream out = new DataOutputStream(gzip)) {
            // Root unnamed compound
            out.writeByte(10); // TAG_Compound
            writeString(out, "");

            writeInt(out, "Version", 2);
            writeInt(out, "DataVersion", capture.dataVersion());
            writeShort(out, "Width", (short) capture.width());
            writeShort(out, "Height", (short) capture.height());
            writeShort(out, "Length", (short) capture.length());
            writeIntArray(out, "Offset", new int[] {capture.offsetX(), capture.offsetY(), capture.offsetZ()});

            // Metadata
            out.writeByte(10);
            writeString(out, "Metadata");
            writeStringTag(out, "Name", schematicName == null ? "ava-claim" : schematicName);
            writeStringTag(out, "Author", "Root-Ava-Core");
            out.writeByte(0); // end Metadata

            // Palette: Compound String -> Int
            out.writeByte(10);
            writeString(out, "Palette");
            for (int i = 0; i < capture.palette().size(); i++) {
                writeInt(out, capture.palette().get(i), i);
            }
            out.writeByte(0); // end Palette

            writeInt(out, "PaletteMax", capture.palette().size());
            writeByteArray(out, "BlockData", encodeVarInts(capture.blockIndices()));

            // Empty BlockEntities list of compounds
            out.writeByte(9); // TAG_List
            writeString(out, "BlockEntities");
            out.writeByte(10); // list type compound
            out.writeInt(0);

            out.writeByte(0); // end root
        }
        return gzipBuf.toByteArray();
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void writeStringTag(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8);
        writeString(out, name);
        writeString(out, value);
    }

    private static void writeInt(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3);
        writeString(out, name);
        out.writeInt(value);
    }

    private static void writeShort(DataOutputStream out, String name, short value) throws IOException {
        out.writeByte(2);
        writeString(out, name);
        out.writeShort(value);
    }

    private static void writeIntArray(DataOutputStream out, String name, int[] values) throws IOException {
        out.writeByte(11);
        writeString(out, name);
        out.writeInt(values.length);
        for (int v : values) {
            out.writeInt(v);
        }
    }

    private static void writeByteArray(DataOutputStream out, String name, byte[] values) throws IOException {
        out.writeByte(7);
        writeString(out, name);
        out.writeInt(values.length);
        out.write(values);
    }

    private static byte[] encodeVarInts(int[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(values.length * 2);
        for (int value : values) {
            int remaining = value;
            while (true) {
                if ((remaining & ~0x7F) == 0) {
                    out.write(remaining);
                    break;
                }
                out.write((remaining & 0x7F) | 0x80);
                remaining >>>= 7;
            }
        }
        return out.toByteArray();
    }
}
