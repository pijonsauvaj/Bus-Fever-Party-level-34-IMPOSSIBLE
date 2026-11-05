package puzzle;

import java.util.*;

public class PuzzleSolver {
    public enum VehicleType {
        CAR(16), VAN(24), BUS(40);
        private final int capacity;
        VehicleType(int capacity) { this.capacity = capacity; }
        public int getCapacity() { return capacity; }
    }

    public static final class Vehicle {
        private static final String JOKER = "?";
        private final String id;
        private final String color;
        private final VehicleType type;

        public Vehicle(String id, String color, VehicleType type) {
            this.id = Objects.requireNonNull(id);
            this.color = Objects.requireNonNull(color);
            this.type = Objects.requireNonNull(type);
        }

        public static Vehicle joker(String id) { return new Vehicle(id, JOKER, VehicleType.BUS); }
        public boolean isJoker() { return color.equals(JOKER); }
        public String getId() { return id; }
        public String getColor() { return color; }
        public int getCapacity() { return type.getCapacity(); }
        @Override public String toString() { return id + "(" + color + "," + type + ")"; }
    }

    public static final class PassengerRow {
        private final String color;
        public PassengerRow(String color) { this.color = Objects.requireNonNull(color); }
        public String getColor() { return color; }
        @Override public String toString() { return "4 " + color; }
    }

    public static final class PassengerPath {
        private final String name;
        private final List<PassengerRow> rows = new ArrayList<>();
        public PassengerPath(String name) { this.name = Objects.requireNonNull(name); }
        public PassengerPath add(String color) { rows.add(new PassengerRow(color)); return this; }
        public String getName() { return name; }
        public List<PassengerRow> getRows() { return Collections.unmodifiableList(rows); }
    }

    public static final class ContainerDef {
        private final String id;
        private final Set<String> blockers;
        private final List<String> contents;
        public ContainerDef(String id, Collection<String> blockers, Collection<String> contents) {
            this.id = Objects.requireNonNull(id);
            this.blockers = new LinkedHashSet<>(blockers);
            this.contents = new ArrayList<>(contents);
        }
    }

    public static final class Level {
        private final Map<String, Vehicle> vehicles = new LinkedHashMap<>();
        private final Set<String> initiallyAvailable = new LinkedHashSet<>();

        private final Map<String, Set<String>> blockersByVehicle = new LinkedHashMap<>();
        private final List<ContainerDef> containers = new ArrayList<>();
        private final List<PassengerPath> paths = new ArrayList<>();

        private final boolean[] usableSlots = { true, true, true, true };

        private int loopCapacity = Integer.MAX_VALUE;

        public Level setLoopCapacity(int rows) { loopCapacity = rows; return this; }

        public boolean printColorBalance() {
            Map<String, Integer> seats = new TreeMap<>(), riders = new TreeMap<>();
            for (Vehicle v : vehicles.values()) if (!v.isJoker()) seats.merge(v.color, v.getCapacity(), Integer::sum);
            for (PassengerPath p : paths) for (PassengerRow r : p.rows) riders.merge(r.color, 4, Integer::sum);
            Set<String> colors = new TreeSet<>(seats.keySet());
            colors.addAll(riders.keySet());
            boolean balanced = true;
            System.out.println("Couleur      places  passagers");
            for (String c : colors) {
                int s = seats.getOrDefault(c, 0), r = riders.getOrDefault(c, 0);
                balanced &= s == r;
                System.out.printf("%-12s %6d %10d %s%n", c, s, r, s == r ? "" : "  <-- ecart " + (r - s));
            }
            return balanced;
        }

        public Level addVehicle(Vehicle vehicle, boolean initiallyVisible) {
            if (vehicles.putIfAbsent(vehicle.id, vehicle) != null)
                throw new IllegalArgumentException("ID vehicule duplique : " + vehicle.id);
            if (initiallyVisible) initiallyAvailable.add(vehicle.id);
            return this;
        }
        public Level addDependency(String targetVehicle, String blockerVehicle) {
            blockersByVehicle.computeIfAbsent(targetVehicle, k -> new LinkedHashSet<>()).add(blockerVehicle);
            return this;
        }
        public Level addContainer(ContainerDef container) {
            containers.add(container);
            return this;
        }
        public Level addPath(PassengerPath path) { paths.add(path); return this; }
        public Level setSlotUsable(int oneBasedSlot, boolean usable) {
            if (oneBasedSlot < 1 || oneBasedSlot > 4) throw new IllegalArgumentException("Place entre 1 et 4");
            usableSlots[oneBasedSlot - 1] = usable;
            return this;
        }
        private void validate() {
            for (String id : initiallyAvailable)
                if (!vehicles.containsKey(id)) throw new IllegalStateException("Vehicule initial inconnu : " + id);
            for (Map.Entry<String, Set<String>> e : blockersByVehicle.entrySet()) {
                if (!vehicles.containsKey(e.getKey())) throw new IllegalStateException("Cible inconnue : " + e.getKey());
                for (String blocker : e.getValue())
                    if (!vehicles.containsKey(blocker)) throw new IllegalStateException("Bloqueur inconnu : " + blocker);
            }
            for (ContainerDef c : containers) {
                for (String id : c.contents)
                    if (!vehicles.containsKey(id)) throw new IllegalStateException("Contenu de conteneur inconnu : " + id);
                for (String id : c.blockers)
                    if (!vehicles.containsKey(id)) throw new IllegalStateException("Bloqueur de conteneur inconnu : " + id);
            }
            if (paths.isEmpty()) throw new IllegalStateException("Le niveau doit avoir au moins un chemin.");
        }
    }

    private static final class State {
        long placed, removed;
        final int[] veh = {-1, -1, -1, -1};
        final int[] color = new int[4], cap = new int[4], boarded = new int[4], seq = new int[4];
        int[] queue;
        int queueSize, next, seqCounter;

        State(int colorCount) { queue = new int[colorCount]; }
        State copy() {
            State s = new State(0);
            s.placed = placed; s.removed = removed;
            System.arraycopy(veh, 0, s.veh, 0, 4);
            System.arraycopy(color, 0, s.color, 0, 4);
            System.arraycopy(cap, 0, s.cap, 0, 4);
            System.arraycopy(boarded, 0, s.boarded, 0, 4);
            System.arraycopy(seq, 0, s.seq, 0, 4);
            s.queue = queue.clone();
            s.queueSize = queueSize; s.next = next; s.seqCounter = seqCounter;
            return s;
        }
    }

    public static final class Solution {
        private final List<String> steps;
        private final long exploredStates;
        private Solution(List<String> steps, long exploredStates) {
            this.steps = steps; this.exploredStates = exploredStates;
        }
        public List<String> getSteps() { return Collections.unmodifiableList(steps); }
        public void print() {
            System.out.println("\n===== SOLUTION TROUVEE =====");
            for (int i = 0; i < steps.size(); i++) System.out.println((i + 1) + ". " + steps.get(i));
            System.out.println("Etats explores : " + exploredStates);
        }
    }

    public static final class Solver {
        private static final int[] JOKER_CAPS = {16, 24, 40};

        private final long maxStates;
        private final boolean[] usableSlots;
        private final int loopCapacity;
        private final List<Vehicle> vehicles;
        private final List<String> colors = new ArrayList<>();
        private final int[] vehColor, vehCap;
        private final boolean[] joker;
        private final long[] blockers;
        private final long initialVisible, allVehicles;
        private final long[] contBlockers, contContents;
        private final int[] rows;
        private final int[][] remainingFrom;

        private final boolean complete;

        private long explored;
        private final Set<String> visited = new HashSet<>();
        private final Deque<String> path = new ArrayDeque<>();
        private List<String> answer;
        int bestProgress = -1;
        List<String> bestHistory = List.of();
        private State bestState;

        public String describeBest() {
            State s = bestState;
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < 4; k++) {
                b.append("  Place ").append(k + 1).append(" : ");
                if (!usableSlots[k]) b.append("indisponible");
                else if (s.veh[k] < 0) b.append("libre");
                else b.append(vehicles.get(s.veh[k]).id).append(" = ").append(colors.get(s.color[k]))
                      .append(' ').append(s.boarded[k]).append('/').append(s.cap[k]);
                b.append('\n');
            }
            b.append("  Boucle (").append(s.queueSize).append('/').append(loopCapacity).append(" rangees) :");
            for (int c = 0; c < colors.size(); c++) if (s.queue[c] > 0) b.append(' ').append(colors.get(c)).append('x').append(s.queue[c]);
            b.append("\n  En attente hors boucle (").append(rows.length - s.next).append(" rangees) :");
            for (int i = s.next; i < rows.length; ) {
                int j = i;
                while (j < rows.length && rows[j] == rows[i]) j++;
                b.append(' ').append(colors.get(rows[i])).append('x').append(j - i);
                i = j;
            }
            return b.toString();
        }

        public Solver(Level level, long maxStates) {
            level.validate();
            this.maxStates = maxStates;
            usableSlots = level.usableSlots;
            loopCapacity = level.loopCapacity;
            vehicles = new ArrayList<>(level.vehicles.values());
            int n = vehicles.size();
            if (n > 64) throw new IllegalStateException("64 vehicules maximum, " + n + " declares");
            Map<String, Integer> vIdx = new HashMap<>();
            for (int i = 0; i < n; i++) vIdx.put(vehicles.get(i).id, i);

            for (PassengerPath p : level.paths) for (PassengerRow r : p.rows) if (!colors.contains(r.color)) colors.add(r.color);
            for (Vehicle v : vehicles) if (!v.isJoker() && !colors.contains(v.color)) colors.add(v.color);
            colors.add("aucune");

            vehColor = new int[n]; vehCap = new int[n]; joker = new boolean[n]; blockers = new long[n];
            long visible = 0;
            for (int i = 0; i < n; i++) {
                Vehicle v = vehicles.get(i);
                joker[i] = v.isJoker();
                vehColor[i] = joker[i] ? -1 : colors.indexOf(v.color);
                vehCap[i] = v.getCapacity();
                for (String b : level.blockersByVehicle.getOrDefault(v.id, Set.of())) blockers[i] |= 1L << vIdx.get(b);
                if (level.initiallyAvailable.contains(v.id)) visible |= 1L << i;
            }
            initialVisible = visible;
            allVehicles = n == 64 ? -1L : (1L << n) - 1;
            contBlockers = new long[level.containers.size()];
            contContents = new long[level.containers.size()];
            for (int k = 0; k < level.containers.size(); k++) {
                ContainerDef c = level.containers.get(k);
                for (String b : c.blockers) contBlockers[k] |= 1L << vIdx.get(b);
                for (String id : c.contents) contContents[k] |= 1L << vIdx.get(id);
            }

            List<Integer> flat = new ArrayList<>();
            for (PassengerPath p : level.paths) for (PassengerRow r : p.rows) flat.add(colors.indexOf(r.color));
            rows = flat.stream().mapToInt(Integer::intValue).toArray();
            remainingFrom = new int[rows.length + 1][colors.size()];
            for (int i = rows.length - 1; i >= 0; i--) {
                remainingFrom[i] = remainingFrom[i + 1].clone();
                remainingFrom[i][rows[i]]++;
            }

            boolean balanced = level.printColorBalance();
            complete = balanced && !vehicles.stream().anyMatch(Vehicle::isJoker);
            System.out.println(complete
                ? "Donnees completes : objectif = finir le niveau."
                : "Donnees partielles : objectif = faire entrer tous les passagers connus dans la boucle.");
        }

        public boolean hitLimit() { return explored >= maxStates; }
        public long explored() { return explored; }

        public Optional<Solution> solve() {
            explored = 0;
            visited.clear();
            path.clear();
            answer = null;
            State initial = new State(colors.size());
            flow(initial);
            search(initial);
            return answer == null ? Optional.empty() : Optional.of(new Solution(answer, explored));
        }

        private boolean search(State s) {
            if (explored >= maxStates) return false;
            if (++explored % 1_000_000 == 0)
                System.out.println("  ... " + explored / 1_000_000 + "M etats, " + visited.size() + " distincts, record " + bestProgress);
            int progress = s.next - s.queueSize;
            if (progress > bestProgress) { bestProgress = progress; bestHistory = new ArrayList<>(path); bestState = s; }
            if (isGoal(s)) { answer = new ArrayList<>(path); return true; }
            if (!visited.add(key(s))) return false;

            int slot = -1;
            for (int k = 0; k < 4 && slot < 0; k++) if (usableSlots[k] && s.veh[k] < 0) slot = k;
            if (slot < 0) return false;

            long visible = initialVisible;
            for (int k = 0; k < contBlockers.length; k++) if ((contBlockers[k] & ~s.placed) == 0) visible |= contContents[k];
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < vehicles.size(); i++)
                if ((visible >> i & 1) == 1 && (s.placed >> i & 1) == 0 && (blockers[i] & ~s.placed) == 0) candidates.add(i);

            candidates.sort(Comparator.comparingInt(i -> joker[i] ? Integer.MIN_VALUE : -s.queue[vehColor[i]]));

            for (int i : candidates) {
                String id = vehicles.get(i).id;
                if (!joker[i]) {
                    if (place(s, slot, i, vehColor[i], vehCap[i], "Sortir " + id + " (place " + (slot + 1) + ")")) return true;
                    continue;
                }

                for (int c = 0; c < colors.size() - 1; c++) {
                    if (s.queue[c] + remainingFrom[s.next][c] == 0) continue;
                    for (int cap : JOKER_CAPS)
                        if (place(s, slot, i, c, cap, "Sortir " + id + " = " + colors.get(c) + " " + cap + " (place " + (slot + 1) + ")"))
                            return true;
                }
                if (place(s, slot, i, colors.size() - 1, 16, "Sortir " + id + " = couleur inutile (place " + (slot + 1) + ")")) return true;
            }
            return false;
        }

        private boolean place(State s, int slot, int i, int c, int cap, String label) {
            State t = s.copy();
            t.placed |= 1L << i;
            t.veh[slot] = i; t.color[slot] = c; t.cap[slot] = cap; t.boarded[slot] = 0; t.seq[slot] = t.seqCounter++;
            flow(t);
            path.addLast(label);
            boolean found = search(t);
            path.removeLast();
            return found;
        }

        private void flow(State s) {
            while (true) {
                board(s);
                if (s.queueSize >= loopCapacity || s.next >= rows.length) return;
                while (s.queueSize < loopCapacity && s.next < rows.length) { s.queue[rows[s.next++]]++; s.queueSize++; }
            }
        }

        private void board(State s) {
            for (int c = 0; c < s.queue.length; c++) {
                while (s.queue[c] > 0) {
                    int k = -1;
                    for (int j = 0; j < 4; j++)
                        if (s.veh[j] >= 0 && s.color[j] == c && s.boarded[j] + 4 <= s.cap[j] && (k < 0 || s.seq[j] < s.seq[k])) k = j;
                    if (k < 0) break;
                    s.queue[c]--; s.queueSize--; s.boarded[k] += 4;
                    if (s.boarded[k] >= s.cap[k]) { s.removed |= 1L << s.veh[k]; s.veh[k] = -1; }
                }
            }
        }

        private boolean isGoal(State s) {
            if (s.next < rows.length) return false;
            return !complete || (s.queueSize == 0 && s.removed == allVehicles);
        }

        private String key(State s) {
            Integer[] parked = java.util.stream.IntStream.range(0, 4).filter(k -> s.veh[k] >= 0).boxed().toArray(Integer[]::new);
            Arrays.sort(parked, Comparator.<Integer>comparingInt(k -> s.color[k]).thenComparingInt(k -> s.seq[k]));
            StringBuilder b = new StringBuilder(32);
            for (int sh = 0; sh < 64; sh += 16) b.append((char) (s.removed >>> sh & 0xFFFF));
            b.append((char) parked.length);
            for (int k : parked) b.append((char) s.veh[k]).append((char) s.color[k]).append((char) s.cap[k]).append((char) s.boarded[k]);
            for (int q : s.queue) b.append((char) q);
            return b.append((char) s.next).toString();
        }
    }

        public static Level createLevelBusFever() {
        Level level = new Level();

        level.addVehicle(new Vehicle("RED_CAR_TOP", "red", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("ORANGE_CAR_TOP", "orange", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("YELLOW_CAR_TOP", "yellow", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("ORANGE_CAR_TOP2", "orange", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("YELLOW_CAR_TOP2", "yellow", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("PINK_CAR_TOP", "pink", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("CYAN_CAR_TOP", "cyan", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("BROWN_CAR_TOP", "brown", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("PURPLE_CAR_TOP", "purple", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("RED_CAR_TOP2", "red", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("RED_VAN_TOP", "red", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("RED_VAN_TOP2", "red", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("ORANGE_VAN_MID", "orange", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("ORANGE_VAN_MID2", "orange", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("PURPLE_VAN_MID", "purple", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("CYAN_VAN_MID", "cyan", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("BROWN_CAR_MID", "brown", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("YELLOW_VAN_MID", "yellow", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("YELLOW_VAN_MID2", "yellow", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("PINK_VAN_MID", "pink", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("PURPLE_VAN_MID2", "purple", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("PINK_VAN_MID2", "pink", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("BLUE_VAN_MID", "blue", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("PINK_CAR_MID", "pink", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("CYAN_CAR_MID", "cyan", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("CYAN_CAR_MID2", "cyan", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("ORANGE_CAR_MID", "orange", VehicleType.CAR), true);
        level.addVehicle(new Vehicle("BROWN_VAN_MID", "brown", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("BROWN_VAN_BOT", "brown", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("RED_VAN_BOT", "red", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("RED_VAN_BOT2", "red", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("YELLOW_VAN_BOT", "yellow", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("YELLOW_VAN_BOT2", "yellow", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("CYAN_VAN_BOT", "cyan", VehicleType.VAN), true);
        level.addVehicle(new Vehicle("PURPLE_BUS_BOT", "purple", VehicleType.BUS), true);
        level.addVehicle(new Vehicle("PINK_BUS_BOT", "pink", VehicleType.BUS), true);
        level.addVehicle(new Vehicle("BROWN_BUS_BOT", "brown", VehicleType.BUS), true);
        level.addVehicle(new Vehicle("RED_BUS_BOT", "red", VehicleType.BUS), true);
        level.addVehicle(new Vehicle("GREEN_BUS_BOT", "green", VehicleType.BUS), true);
        level.addVehicle(new Vehicle("DARKBLUE_BUS_BOT", "dark_blue", VehicleType.BUS), true);
        level.addVehicle(new Vehicle("RED_BUS_BOT2", "red", VehicleType.BUS), true);

        level.addDependency("DARKBLUE_BUS_BOT", "PURPLE_BUS_BOT");
        level.addDependency("GREEN_BUS_BOT", "RED_BUS_BOT");
        level.addDependency("RED_BUS_BOT2", "PINK_BUS_BOT");
        level.addDependency("YELLOW_VAN_BOT2", "RED_VAN_BOT2");
        level.addDependency("RED_BUS_BOT", "DARKBLUE_BUS_BOT");

        level.addDependency("PURPLE_BUS_BOT", "PINK_CAR_MID");
        level.addDependency("PURPLE_BUS_BOT", "CYAN_CAR_MID");
        level.addDependency("PURPLE_BUS_BOT", "CYAN_VAN_MID");
        level.addDependency("PURPLE_BUS_BOT", "RED_CAR_TOP2");
        level.addDependency("PURPLE_BUS_BOT", "RED_CAR_TOP");
        level.addDependency("PURPLE_BUS_BOT", "YELLOW_CAR_TOP");
        level.addDependency("PURPLE_BUS_BOT", "ORANGE_VAN_MID");

        level.addDependency("BROWN_BUS_BOT", "PURPLE_BUS_BOT");
        level.addDependency("BROWN_BUS_BOT", "PINK_VAN_MID");
        level.addDependency("BROWN_BUS_BOT", "PURPLE_VAN_MID2");

        level.addDependency("BROWN_VAN_BOT", "RED_VAN_BOT");

        level.addDependency("PINK_BUS_BOT", "CYAN_CAR_MID");
        level.addDependency("PINK_BUS_BOT", "ORANGE_CAR_MID");
        level.addDependency("PINK_BUS_BOT", "CYAN_VAN_MID");
        level.addDependency("PINK_BUS_BOT", "ORANGE_CAR_TOP");
        level.addDependency("PINK_BUS_BOT", "ORANGE_CAR_TOP2");

        level.addDependency("PINK_VAN_MID2", "PURPLE_VAN_MID");

        level.addDependency("PINK_VAN_MID", "CYAN_CAR_TOP");

        level.addDependency("PINK_CAR_MID", "ORANGE_VAN_MID");
        level.addDependency("ORANGE_VAN_MID", "ORANGE_CAR_TOP");

        level.addDependency("CYAN_CAR_MID", "ORANGE_VAN_MID2");
        level.addDependency("ORANGE_VAN_MID2", "ORANGE_CAR_TOP2");

        level.addDependency("BLUE_VAN_MID", "CYAN_VAN_MID");

        level.addDependency("CYAN_VAN_MID", "RED_CAR_TOP2");
        level.addDependency("RED_CAR_TOP2", "RED_CAR_TOP");

        level.addDependency("CYAN_CAR_MID", "BROWN_CAR_MID");
        level.addDependency("CYAN_CAR_MID", "YELLOW_CAR_TOP");
        level.addDependency("CYAN_CAR_MID", "YELLOW_CAR_TOP2");

        level.addDependency("ORANGE_CAR_MID", "BROWN_CAR_MID");
        level.addDependency("ORANGE_CAR_MID", "YELLOW_CAR_TOP");

        level.addDependency("BROWN_CAR_MID", "PURPLE_VAN_MID");
        level.addDependency("BROWN_CAR_MID", "BROWN_VAN_MID");
        level.addDependency("BROWN_CAR_MID", "YELLOW_VAN_MID");

        level.addDependency("PURPLE_VAN_MID", "PURPLE_CAR_TOP");
        level.addDependency("PURPLE_VAN_MID", "YELLOW_CAR_TOP");

        level.addDependency("YELLOW_VAN_MID", "RED_VAN_TOP2");
        level.addDependency("YELLOW_VAN_MID", "PURPLE_CAR_TOP");

        level.addDependency("BROWN_VAN_MID", "BROWN_VAN_BOT");
        level.addDependency("BROWN_VAN_MID", "YELLOW_VAN_BOT2");

        level.addDependency("YELLOW_VAN_BOT2", "RED_VAN_BOT2");

        level.addPath(new PassengerPath("Gauche")
            .add("pink").add("pink").add("pink").add("pink")
            .add("pink").add("pink").add("pink")
            .add("yellow").add("yellow").add("yellow").add("yellow")
            .add("yellow").add("yellow").add("yellow")
            .add("brown").add("brown").add("brown").add("brown")
            .add("brown").add("brown").add("brown")
            .add("green").add("green").add("green").add("green")
            .add("green").add("green").add("green").add("green")
            .add("green").add("green")
            .add("cyan").add("cyan")
            .add("dark_blue")
            .add("red")
            .add("cyan").add("cyan").add("cyan").add("cyan").add("cyan")
            .add("orange").add("orange").add("orange").add("orange")
            .add("orange").add("orange").add("orange")
            .add("purple").add("purple").add("purple").add("purple")
            .add("purple").add("purple").add("purple")
            .add("blue").add("blue").add("blue")
            .add("blue").add("blue").add("blue")
            .add("yellow").add("yellow").add("yellow")
            .add("red").add("red").add("red")
            .add("brown").add("brown").add("brown").add("brown")
            .add("brown").add("brown").add("brown").add("brown")
            .add("yellow").add("yellow").add("yellow").add("yellow")
            .add("yellow").add("yellow").add("yellow")
            .add("purple").add("purple").add("purple")
            .add("purple").add("purple").add("purple")
            .add("cyan").add("cyan").add("cyan")
            .add("cyan").add("cyan").add("cyan")
            .add("pink").add("pink").add("pink").add("pink")
            .add("pink").add("pink").add("pink").add("pink")
            .add("red").add("red").add("red")
            .add("red").add("red").add("red")
        );

        level.addPath(new PassengerPath("Droite")
            .add("dark_blue").add("dark_blue").add("dark_blue")
            .add("dark_blue").add("dark_blue").add("dark_blue")
            .add("dark_blue").add("dark_blue").add("dark_blue")
            .add("dark_blue").add("dark_blue").add("dark_blue")
            .add("dark_blue").add("dark_blue").add("dark_blue")
            .add("dark_blue").add("dark_blue").add("dark_blue")
            .add("dark_blue").add("dark_blue").add("dark_blue")
        );

        level.setLoopCapacity(35);

        for (String[] c : new String[][] {{"CONT_G_", "DARKBLUE_BUS_BOT"}, {"CONT_D_", "RED_BUS_BOT2"}}) {
            String previous = c[1];
            for (int k = 2; k <= 5; k++) {
                level.addVehicle(Vehicle.joker(c[0] + k), true);
                level.addDependency(c[0] + k, previous);
                previous = c[0] + k;
            }
        }

        return level;
    }

    static void selfTest() {
        Level ok = new Level()
            .addVehicle(new Vehicle("A", "blue", VehicleType.CAR), true)
            .addVehicle(new Vehicle("B", "red", VehicleType.CAR), true)
            .addDependency("A", "B")
            .addPath(new PassengerPath("p").add("red").add("red").add("red").add("red")
                .add("blue").add("blue").add("blue").add("blue"))
            .setLoopCapacity(1);
        ok.setSlotUsable(2, false).setSlotUsable(3, false).setSlotUsable(4, false);
        assert new Solver(ok, 1000).solve().isPresent() : "niveau soluble non resolu";

        Level ko = new Level()
            .addVehicle(new Vehicle("A", "blue", VehicleType.CAR), true)
            .addVehicle(new Vehicle("B", "red", VehicleType.CAR), true)
            .addDependency("A", "B")
            .addPath(new PassengerPath("p").add("blue").add("blue").add("blue").add("blue")
                .add("red").add("red").add("red").add("red"))
            .setLoopCapacity(1);
        ko.setSlotUsable(2, false).setSlotUsable(3, false).setSlotUsable(4, false);
        Solver s = new Solver(ko, 1000);
        assert s.solve().isEmpty() && !s.hitLimit() : "niveau impossible declare soluble";

        Level withJoker = new Level()
            .addVehicle(Vehicle.joker("J"), true)
            .addPath(new PassengerPath("p").add("blue").add("blue").add("blue").add("blue"))
            .setLoopCapacity(1);
        assert new Solver(withJoker, 1000).solve().isPresent() : "joker non utilise";
        Level noJoker = new Level()
            .addVehicle(new Vehicle("R", "red", VehicleType.CAR), true)
            .addPath(new PassengerPath("p").add("blue").add("blue").add("blue").add("blue"))
            .setLoopCapacity(1);
        Solver s2 = new Solver(noJoker, 1000);
        assert s2.solve().isEmpty() && !s2.hitLimit() : "rangees bleues sans vehicule bleu";
        System.out.println("Auto-test OK");
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("test")) { selfTest(); return; }
        Level level = createLevelBusFever();
        Solver solver = new Solver(level, args.length > 0 ? Long.parseLong(args[0]) : 100_000_000L);
        Optional<Solution> solution = solver.solve();
        if (solution.isPresent()) {
            solution.get().print();
        } else if (solver.hitLimit()) {
            System.out.println("Limite atteinte sans solution : AUCUNE conclusion possible.");
        } else {
            System.out.println("Recherche exhaustive : aucune solution dans ce modele.");
        }
        if (solution.isEmpty()) {
            System.out.println("Meilleure progression : " + solver.bestProgress + " rangees embarquees, via :");
            solver.bestHistory.forEach(s -> System.out.println("  " + s));
            System.out.println("Situation a ce moment-la (aucune suite ne fait embarquer plus de rangees) :");
            System.out.println(solver.describeBest());
        }
        System.out.println("Etats explores : " + solver.explored());
    }
}
