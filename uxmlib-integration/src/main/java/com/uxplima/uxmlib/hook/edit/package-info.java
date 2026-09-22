/**
 * The seam onto the world editors: WorldEdit and the drop in replacements that answer its API.
 *
 * <p>The library holds the types and the presence check together, so a consumer can guard a boundary
 * without naming a class that may not be on the server. What a boundary is stays with the consumer: this
 * package knows that an edit touches blocks and that somebody may say no, and nothing else.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmlib.hook.edit;
