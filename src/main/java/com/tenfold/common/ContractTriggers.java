package com.tenfold.common;

import com.google.gson.JsonObject;
import com.tenfold.Tenfold;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * Custom advancement triggers for the contract system.
 * <p>
 * Vanilla has no hook for "issued a kill contract", so each milestone gets its
 * own trigger with a numeric threshold. The JSON criteria live in
 * {@code data/tenfold/advancements} and look like:
 *
 * <pre>{@code
 * "criteria": { "met": { "trigger": "tenfold:contract_done", "conditions": { "count": 4 } } }
 * }</pre>
 */
public final class ContractTriggers {

    /** Fired when the blade locks onto a creature; count = lifetime contracts issued. */
    public static final CountedTrigger CONTRACT_ISSUED = new CountedTrigger("contract_issued");

    /** Fired when a full contract (three kills) is fulfilled; count = contracts completed in a row. */
    public static final CountedTrigger CONTRACT_DONE = new CountedTrigger("contract_done");

    /** Fired on every breach; count = breaches in a row. */
    public static final CountedTrigger CONTRACT_BROKEN = new CountedTrigger("contract_broken");

    /** Fired the first time the IRA potion is drunk; count is always 1. */
    public static final CountedTrigger OATH_DRUNK = new CountedTrigger("oath_drunk");

    /** Criterion instance carrying a plain integer threshold. */
    public static final class CountTrigger extends AbstractCriterionTriggerInstance {
        private final int required;

        public CountTrigger(ResourceLocation id, ContextAwarePredicate player, int required) {
            super(id, player);
            this.required = required;
        }

        public boolean matches(int actual) {
            return actual >= this.required;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.addProperty("count", this.required);
            return json;
        }
    }

    /** Named subclass so we can expose a public trigger hook. */
    public static final class CountedTrigger extends SimpleCriterionTrigger<CountTrigger> {
        private final ResourceLocation id;

        CountedTrigger(String path) {
            this.id = new ResourceLocation(Tenfold.MOD_ID, path);
        }

        @Override
        public ResourceLocation getId() {
            return this.id;
        }

        @Override
        protected CountTrigger createInstance(JsonObject json, ContextAwarePredicate player,
                                              DeserializationContext context) {
            return new CountTrigger(this.id, player, GsonHelper.getAsInt(json, "count", 1));
        }

        /** Public entry point; {@code SimpleCriterionTrigger#trigger} is protected. */
        public void fire(ServerPlayer player, int value) {
            this.trigger(player, instance -> instance.matches(value));
        }
    }

    public static void fire(CountedTrigger trigger, ServerPlayer player, int value) {
        trigger.fire(player, value);
    }

    private ContractTriggers() {
    }
}
