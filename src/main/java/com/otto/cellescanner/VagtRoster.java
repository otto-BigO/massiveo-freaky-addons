package com.otto.cellescanner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The FreakyVille staff roster, by exact Minecraft username.
 *
 * These are real players, so matching is on the username itself. The older
 * heuristic looked for the word "vagt" in a name or tag, which matches none of
 * these accounts and would also catch any ordinary player whose name happens to
 * contain it. Usernames are compared lower case because Mojang treats them as
 * case insensitive for display purposes.
 */
public final class VagtRoster {

    public enum Rank {
        VAGT("A Vagt"),
        OFFICER("Officer"),
        INSPEKTOR("Inspekt\u00f8r"),
        DIREKTOR("Direkt\u00f8r");

        private final String label;

        Rank(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Map<String, Rank> ROSTER;

    static {
        Map<String, Rank> m = new HashMap<String, Rank>();

        // DIREKTOR
        m.put("hijndk", Rank.DIREKTOR);
        m.put("lucas_as", Rank.DIREKTOR);

        // INSPEKTOR
        m.put("sykopingvin", Rank.INSPEKTOR);

        // OFFICER
        m.put("emiiilovich", Rank.OFFICER);
        m.put("gyymmr", Rank.OFFICER);
        m.put("legendenvoldby", Rank.OFFICER);
        m.put("slyqnn", Rank.OFFICER);
        m.put("svambz", Rank.OFFICER);
        m.put("sw1chhh", Rank.OFFICER);
        m.put("v3gaaa", Rank.OFFICER);
        m.put("zeqnix", Rank.OFFICER);

        // VAGT
        m.put("0ks3n", Rank.VAGT);
        m.put("123dipper", Rank.VAGT);
        m.put("3d4l", Rank.VAGT);
        m.put("90grimlock90", Rank.VAGT);
        m.put("__kn0x__", Rank.VAGT);
        m.put("_annemette_v3", Rank.VAGT);
        m.put("_flexiii_", Rank.VAGT);
        m.put("_g1ey", Rank.VAGT);
        m.put("_pinkbee_", Rank.VAGT);
        m.put("adamabe2009", Rank.VAGT);
        m.put("al3xfsjensen", Rank.VAGT);
        m.put("aleex3nder", Rank.VAGT);
        m.put("anderstekkitv2", Rank.VAGT);
        m.put("ant0n__", Rank.VAGT);
        m.put("askevii", Rank.VAGT);
        m.put("badensoe", Rank.VAGT);
        m.put("bangebuks", Rank.VAGT);
        m.put("betaboris", Rank.VAGT);
        m.put("blebman", Rank.VAGT);
        m.put("blockgaaramok", Rank.VAGT);
        m.put("bongo_bent", Rank.VAGT);
        m.put("buchwaldtnr2", Rank.VAGT);
        m.put("c4trinesejr", Rank.VAGT);
        m.put("cubra", Rank.VAGT);
        m.put("d1screets", Rank.VAGT);
        m.put("daniel_kongen", Rank.VAGT);
        m.put("disne7", Rank.VAGT);
        m.put("doecproductions", Rank.VAGT);
        m.put("ducks_o", Rank.VAGT);
        m.put("el_broero07", Rank.VAGT);
        m.put("elchapo_akav2", Rank.VAGT);
        m.put("forfatter", Rank.VAGT);
        m.put("freddyforcarry", Rank.VAGT);
        m.put("gaardxd", Rank.VAGT);
        m.put("girlboost", Rank.VAGT);
        m.put("idag_", Rank.VAGT);
        m.put("itsvictorv2_", Rank.VAGT);
        m.put("jasoninflation", Rank.VAGT);
        m.put("jeg3lskerv4fler", Rank.VAGT);
        m.put("juhll", Rank.VAGT);
        m.put("jullekrog", Rank.VAGT);
        m.put("jyttahxd", Rank.VAGT);
        m.put("k0caa", Rank.VAGT);
        m.put("kaho0t", Rank.VAGT);
        m.put("kasper_kejser", Rank.VAGT);
        m.put("kattentheo", Rank.VAGT);
        m.put("kevinkris", Rank.VAGT);
        m.put("killerbody234", Rank.VAGT);
        m.put("kio4567_", Rank.VAGT);
        m.put("l4uisesejr", Rank.VAGT);
        m.put("lasseaab", Rank.VAGT);
        m.put("linusfrede", Rank.VAGT);
        m.put("luffegamerv4", Rank.VAGT);
        m.put("lugi0012", Rank.VAGT);
        m.put("macaaaroni", Rank.VAGT);
        m.put("magnus_fed", Rank.VAGT);
        m.put("mathiaske", Rank.VAGT);
        m.put("mini_pleb", Rank.VAGT);
        m.put("molin18", Rank.VAGT);
        m.put("muni_jr", Rank.VAGT);
        m.put("neeeeed", Rank.VAGT);
        m.put("notmakker", Rank.VAGT);
        m.put("nuddi_gaming", Rank.VAGT);
        m.put("oliber1337", Rank.VAGT);
        m.put("oneglitchs", Rank.VAGT);
        m.put("orkenrottendiego", Rank.VAGT);
        m.put("oskarfrede", Rank.VAGT);
        m.put("ostepopss", Rank.VAGT);
        m.put("ozzydk", Rank.VAGT);
        m.put("papeske", Rank.VAGT);
        m.put("pizzajarlen", Rank.VAGT);
        m.put("platov1", Rank.VAGT);
        m.put("pokiposcar", Rank.VAGT);
        m.put("pr1nglesman", Rank.VAGT);
        m.put("r1ck99", Rank.VAGT);
        m.put("rallemuzen", Rank.VAGT);
        m.put("romeo_brix", Rank.VAGT);
        m.put("sanderhaj", Rank.VAGT);
        m.put("shambygod", Rank.VAGT);
        m.put("siintro", Rank.VAGT);
        m.put("skayerboy", Rank.VAGT);
        m.put("skyperch_", Rank.VAGT);
        m.put("sparegrisenn", Rank.VAGT);
        m.put("t1ldsen", Rank.VAGT);
        m.put("technof1shq", Rank.VAGT);
        m.put("tewrrv", Rank.VAGT);
        m.put("the_snowtroll", Rank.VAGT);
        m.put("theguubdx", Rank.VAGT);
        m.put("tiske_taskev2", Rank.VAGT);
        m.put("tobi_cake", Rank.VAGT);
        m.put("truckerhd", Rank.VAGT);
        m.put("tyenda", Rank.VAGT);
        m.put("vengeld_", Rank.VAGT);
        m.put("viktorfanzyman", Rank.VAGT);
        m.put("warpv2", Rank.VAGT);
        m.put("wikzzzz", Rank.VAGT);
        m.put("xpamgo", Rank.VAGT);
        m.put("yelruts", Rank.VAGT);
        ROSTER = Collections.unmodifiableMap(m);
    }

    private VagtRoster() {
    }

    /** True when this exact username is on the staff roster. */
    public static boolean contains(String username) {
        return rankOf(username) != null;
    }

    /** The staff rank for this username, or null when they are not staff. */
    public static Rank rankOf(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        return ROSTER.get(username.toLowerCase());
    }

    /** How many accounts are on the roster. */
    public static int size() {
        return ROSTER.size();
    }
}
