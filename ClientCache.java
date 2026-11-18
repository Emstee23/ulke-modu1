import java.util.ArrayList;
import java.util.List;

public class ClientCache {
    public static List<ModNetworking.CountrySyncData> countries = new ArrayList<>();
    public static String playerCountryId = "";
    public static int warDeaths = 0;
    public static int maxWarDeaths = 5;
    public static boolean inWar = false;
    public static int prepSecondsLeft = 0;

    public static boolean hasCountry() {
        return playerCountryId != null && !playerCountryId.isEmpty();
    }

    public static ModNetworking.CountrySyncData getMyCountry() {
        for (ModNetworking.CountrySyncData c : countries) {
            if (c.id().equals(playerCountryId)) return c;
        }
        return null;
    }
}
