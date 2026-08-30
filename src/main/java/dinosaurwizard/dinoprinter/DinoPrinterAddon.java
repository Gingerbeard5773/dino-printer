package dinosaurwizard.dinoprinter;

import dinosaurwizard.dinoprinter.modules.*;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class DinoPrinterAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Dino Printer");

        // Modules
        Modules.get().add(new DinoPrinter());
    }

    @Override
    public void onRegisterCategories() {
        // Custom category not defined
    }

    @Override
    public String getPackage() {
        return "dinosaurwizard.dinoprinter";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Gingerbeard5773", "dino-printer");
    }
}
