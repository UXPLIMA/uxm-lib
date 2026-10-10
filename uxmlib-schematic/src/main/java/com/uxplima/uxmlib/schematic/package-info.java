/**
 * Structures as files: a {@link com.uxplima.uxmlib.schematic.Schematic} is a box of block states with the
 * block entities and entities inside it, read from and written to the Sponge {@code .schem} format by
 * {@link com.uxplima.uxmlib.schematic.format}, and pasted into and captured from a world by
 * {@link com.uxplima.uxmlib.schematic.paper}, a region at a time so it is safe on Folia.
 *
 * <p>No WorldEdit and no FastAsyncWorldEdit: a plugin that pastes a structure works on a server that has
 * neither, and reads the files both of them write.
 */
@NullMarked
package com.uxplima.uxmlib.schematic;

import org.jspecify.annotations.NullMarked;
