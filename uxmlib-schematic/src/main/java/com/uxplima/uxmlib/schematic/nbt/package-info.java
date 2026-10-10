/**
 * Named Binary Tag, the format every Minecraft structure file is written in.
 *
 * <p>{@link com.uxplima.uxmlib.schematic.nbt.NbtReader} reads a tree under limits, so a file of unknown origin can
 * claim neither more memory than it has bytes nor a nesting deeper than a real file has, and
 * {@link com.uxplima.uxmlib.schematic.nbt.NbtWriter} writes one back. The tags are plain records.
 */
@NullMarked
package com.uxplima.uxmlib.schematic.nbt;

import org.jspecify.annotations.NullMarked;
