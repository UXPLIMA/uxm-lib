package com.uxplima.uxmlib.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A precomputed, moving slot-set animation: a list of frames, each frame the set of slots lit at that step
 * (a "border walk", "marching ants" or "sweep"). Frames are computed once up front and then advanced on a
 * tick clock by {@link SlotAnimation}; this type holds only the pure maths so the frame advance and the
 * changed-slot diff are unit-testable with no live inventory.
 *
 * <p>The diff is the load-bearing half: between two frames only the slots that actually changed are touched:
 * slots in the old frame but not the new are cleared, slots in the new frame are (re)lit. A repaint that
 * rewrote every slot each tick would clobber overlaid buttons and flood the client; the diff avoids both.
 *
 * <p>Generators ({@link #clockwiseBorder} and {@link #sweep}) build the common shapes; {@link #of} wraps an
 * arbitrary frame list.
 */
public final class SlotPattern {

    private final List<List<Integer>> frames;

    private SlotPattern(List<List<Integer>> frames) {
        this.frames = frames;
    }

    /**
     * Wrap an explicit list of frames. Each inner list is the slots lit in that frame; order within a frame
     * is preserved but not significant. At least one frame is required.
     */
    public static SlotPattern of(List<List<Integer>> frames) {
        Objects.requireNonNull(frames, "frames");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("a slot pattern needs at least one frame");
        }
        List<List<Integer>> copy = new ArrayList<>(frames.size());
        for (List<Integer> frame : frames) {
            copy.add(List.copyOf(Objects.requireNonNull(frame, "frame")));
        }
        return new SlotPattern(List.copyOf(copy));
    }

    /** The number of frames in the cycle. */
    public int frameCount() {
        return frames.size();
    }

    /** The slots lit in frame {@code index} (0-based). */
    public List<Integer> frame(int index) {
        if (index < 0 || index >= frames.size()) {
            throw new IllegalArgumentException("frame index must be 0.." + (frames.size() - 1));
        }
        return frames.get(index);
    }

    /** The frame index shown at {@code ticks}, wrapping through the cycle. */
    public int frameIndexAt(long ticks) {
        long mod = Math.floorMod(ticks, (long) frames.size());
        return (int) mod;
    }

    /** The slots lit at {@code ticks}, wrapping through the cycle. */
    public List<Integer> slotsAt(long ticks) {
        return frames.get(frameIndexAt(ticks));
    }

    /**
     * The slots that change going from {@code fromFrame} to {@code toFrame}: those to clear (lit in the old
     * frame, dark in the new) and those to light (every slot in the new frame). Re-lighting every new-frame
     * slot keeps the highlight correct even where a slot stayed lit but its neighbour repaint cleared it.
     */
    public FrameDiff diff(int fromFrame, int toFrame) {
        Set<Integer> from = new LinkedHashSet<>(frame(fromFrame));
        List<Integer> to = frame(toFrame);
        Set<Integer> toSet = new LinkedHashSet<>(to);
        List<Integer> toClear = new ArrayList<>();
        for (int slot : from) {
            if (!toSet.contains(slot)) {
                toClear.add(slot);
            }
        }
        return new FrameDiff(List.copyOf(toClear), List.copyOf(to));
    }

    /**
     * A border-walk pattern: a window of {@code litCount} adjacent slots that walks clockwise around the
     * outer ring of a {@code width}×{@code height} grid, one slot per frame, starting {@code offset} slots
     * along the ring. With {@code litCount == 1} a single highlight marches around the edge; a larger window
     * makes a "comet" trail.
     */
    public static SlotPattern clockwiseBorder(int width, int height, int litCount, int offset) {
        List<Integer> ring = orderedBorderSlots(width, height, offset);
        if (litCount < 1 || litCount > ring.size()) {
            throw new IllegalArgumentException("litCount must be 1.." + ring.size());
        }
        List<List<Integer>> frames = new ArrayList<>(ring.size());
        for (int start = 0; start < ring.size(); start++) {
            List<Integer> window = new ArrayList<>(litCount);
            for (int step = 0; step < litCount; step++) {
                window.add(ring.get((start + step) % ring.size()));
            }
            frames.add(window);
        }
        return of(frames);
    }

    /**
     * A sweep pattern: one whole column lit per frame, advancing left to right across a
     * {@code width}×{@code height} grid. A vertical "scanner" line.
     */
    public static SlotPattern sweep(int width, int height) {
        checkDimensions(width, height);
        List<List<Integer>> frames = new ArrayList<>(width);
        for (int col = 0; col < width; col++) {
            List<Integer> column = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                column.add(row * width + col);
            }
            frames.add(column);
        }
        return of(frames);
    }

    /**
     * The slots of the outer border of a {@code width}×{@code height} grid in clockwise order from the top
     * left (across the top row, down the right column, back across the bottom row, up the left column),
     * rotated to start {@code offset} slots along that ring. Used to build {@link #clockwiseBorder} and by
     * fillers that want to walk the edge.
     */
    public static List<Integer> orderedBorderSlots(int width, int height, int offset) {
        checkDimensions(width, height);
        List<Integer> ring = new ArrayList<>();
        for (int col = 0; col < width; col++) {
            ring.add(col); // top row, left to right
        }
        for (int row = 1; row < height; row++) {
            ring.add(row * width + (width - 1)); // right column, top to bottom
        }
        for (int col = width - 2; col >= 0 && height > 1; col--) {
            ring.add((height - 1) * width + col); // bottom row, right to left
        }
        for (int row = height - 2; row >= 1; row--) {
            ring.add(row * width); // left column, bottom to top
        }
        return rotate(ring, offset);
    }

    private static List<Integer> rotate(List<Integer> ring, int offset) {
        if (ring.isEmpty()) {
            return List.of();
        }
        int shift = Math.floorMod(offset, ring.size());
        List<Integer> rotated = new ArrayList<>(ring.size());
        for (int index = 0; index < ring.size(); index++) {
            rotated.add(ring.get((index + shift) % ring.size()));
        }
        return List.copyOf(rotated);
    }

    private static void checkDimensions(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("width and height must be >= 1");
        }
    }

    /** The slots to clear and the slots to light when advancing one frame; see {@link #diff}. */
    public record FrameDiff(List<Integer> toClear, List<Integer> toLight) {
        public FrameDiff {
            toClear = List.copyOf(Objects.requireNonNull(toClear, "toClear"));
            toLight = List.copyOf(Objects.requireNonNull(toLight, "toLight"));
        }
    }
}
