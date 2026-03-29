package net.blueva.arcade.modules.race.listener;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.race.RaceGameManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public class RaceDamageListener extends EntityEventSystem<EntityStore, Damage> {

    private final RaceGameManager gameManager;

    public RaceDamageListener(RaceGameManager gameManager) {
        super(Damage.class);
        this.gameManager = gameManager;
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        if (damage.isCancelled()) {
            return;
        }

        Ref<EntityStore> victimRef = archetypeChunk.getReferenceTo(index);
        if (victimRef == null) {
            return;
        }

        PlayerRef victimRefComponent = store.getComponent(victimRef, PlayerRef.getComponentType());
        Player victimPlayer = store.getComponent(victimRef, Player.getComponentType());
        if (victimRefComponent == null || victimPlayer == null) {
            return;
        }

        GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context =
                gameManager.getGameContext(victimPlayer);
        if (context == null || !context.isPlayerPlaying(victimPlayer)) {
            return;
        }

        damage.setCancelled(true);
        clearKnockback(damage, victimRef, store, commandBuffer);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }

    private void clearKnockback(Damage damage,
                                Ref<EntityStore> ref,
                                Store<EntityStore> store,
                                CommandBuffer<EntityStore> commandBuffer) {
        damage.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
        KnockbackComponent knockback = store.getComponent(ref, KnockbackComponent.getComponentType());
        if (knockback != null) {
            knockback.setVelocity(com.hypixel.hytale.math.vector.Vector3d.ZERO);
            knockback.setDuration(0.0F);
        }
        commandBuffer.tryRemoveComponent(ref, KnockbackComponent.getComponentType());
    }
}
