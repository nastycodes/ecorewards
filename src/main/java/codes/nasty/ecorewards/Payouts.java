package codes.nasty.ecorewards;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.andy.ecobal.api.EconomyManager;

/**
 * Collects earned money and hands it to EcoBal in batches.
 * EcoBal saves its whole balance file on every deposit, so one deposit per mined block would stall the server.
 */
public final class Payouts {
	private static final Map<UUID, Double> PENDING = new HashMap<>();
	private static int ticksSinceFlush;

	private Payouts() {
	}

	public static void add(UUID player, double amount) {
		if (amount <= 0.0) return;
		if (RewardConfig.get().payoutIntervalSeconds == 0) {
			deposit(player, amount);
		} else {
			PENDING.merge(player, amount, Double::sum);
		}
	}

	public static void tick() {
		if (++ticksSinceFlush >= RewardConfig.get().payoutIntervalSeconds * 20) {
			flushAll();
		}
	}

	public static void flush(UUID player) {
		Double amount = PENDING.remove(player);
		if (amount != null) deposit(player, amount);
	}

	public static void flushAll() {
		ticksSinceFlush = 0;
		if (PENDING.isEmpty()) return;
		Map<UUID, Double> batch = new HashMap<>(PENDING);
		PENDING.clear();
		batch.forEach(Payouts::deposit);
	}

	public static double roundMoney(double amount) {
		return Math.round(amount * 100.0) / 100.0;
	}

	private static void deposit(UUID player, double amount) {
		double rounded = roundMoney(amount);
		if (rounded <= 0.0) return;
		try {
			EconomyManager.silentDeposit(player, rounded);
		} catch (RuntimeException e) {
			EcoRewards.LOGGER.error("EcoBal deposit of {} for {} failed", rounded, player, e);
		}
	}
}
