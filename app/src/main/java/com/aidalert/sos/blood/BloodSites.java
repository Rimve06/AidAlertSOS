package com.aidalert.sos.blood;

/**
 * The Bangladesh blood platforms AidAlert links to. AidAlert does not copy their donor lists (they hold personal data and
 * have their own terms); a person saves the donors they found, and registers on each site themselves.
 * Pure Java.
 */
public final class BloodSites {
    private BloodSites() {}

    public static final class Site {
        public final String name, url, note;
        Site(String name, String url, String note) {
            this.name = name;
            this.url = url;
            this.note = note;
        }
    }

    public static final Site[] ALL = {
            new Site("RoktoData", "https://www.roktodata.org/", "Donor search and registration"),
            new Site("BMHBD", "https://www.bmhbd.org/", "Blood donor network"),
            new Site("Blood Donor Force of Bangladesh", "https://bdfb.info/", "Donor and request platform"),
            new Site("IOM Blood Directory", "https://iom.edu.bd/ibd/", "Directory of donors and blood banks"),
            new Site("Bloodman", "https://bloodman.org/", "Donor search and registration"),
    };

    public static final String ONE_BLOOD_NOTE =
            "One Blood Network was announced on 14 June 2025, but it is not confirmed to be running, so it is not linked here.";

    public static String[] names() {
        String[] n = new String[ALL.length];
        for (int i = 0; i < n.length; i++) n[i] = ALL[i].name;
        return n;
    }

    /**
     * The details a person types into every partner's registration form, in one block they can copy once.
     * lastDonationDays &lt; 0 means never donated.
     */
    public static String registrationText(String name, String group, String phone, String district, String area, long lastDonationDays) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(name == null ? "" : name).append('\n');
        sb.append("Blood group: ").append(group == null ? "" : group).append('\n');
        sb.append("Mobile: ").append(phone == null ? "" : phone).append('\n');
        sb.append("District: ").append(district == null ? "" : district).append('\n');
        if (area != null && !area.isEmpty()) sb.append("Area: ").append(area).append('\n');
        sb.append("Last donation: ").append(lastDonationDays < 0 ? "never" : lastDonationDays + " days ago");
        return sb.toString();
    }

    /** RoktoData lists donors per district at /donors/{district}; other sites open on their home page. */
    public static String searchUrl(Site site, String district) {
        if (site.url.contains("roktodata.org") && district != null && !district.trim().isEmpty()) {
            return site.url + "donors/" + slug(district);
        }
        return site.url;
    }

    /** "Cox's Bazar" -> "coxs-bazar" */
    public static String slug(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
