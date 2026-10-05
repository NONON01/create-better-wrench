package com.nonono.createbetterwrench.assemble;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.mode.ProcessKind;
import com.simibubi.create.AllRecipeTypes;
import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「加工」模式里的<b>锻造</b>路径 —— 用手里的锤子代替动力冲压机, 直接执行机械动力的冲压配方。
 *
 * <h2>唯一的落点: 已锁定的置物台</h2>
 * <table border="1">
 *   <caption>与 Create 的对应关系</caption>
 *   <tr><th>落点</th><th>配方类型</th><th>Create 中文名</th><th>输入</th></tr>
 *   <tr><td>已锁定的<b>置物台</b></td><td>{@code create:pressing}</td><td>冲压</td><td>单个物品</td></tr>
 * </table>
 *
 * <p><b>工作盆支持已整体移除</b>(2026-10-03): 工作盆的物品归集方式与置物台不同, 两者的判定冲突过多,
 * 因此锻造只保留置物台这一条落点。</p>
 */

public final class Forging {


    /**
     * 「锻造」认的锤类标签(1.20.1 同时接受两种生态的命名)。
     *
     * <p>{@code forge:tools/hammer} 是 1.20.1 的通行约定: Forge 自身的公共标签全部放在 {@code forge:} 下
     * (Forge 47.1.3 的 jar 里有 370 个 {@code data/forge/tags/...}, 没有任何 {@code data/c/tags/...}),
     * Create 6.0.8 的扳手标签同样是 {@code forge:tools/wrench}。{@code c:tools/hammer} 是 1.21 以后
     * NeoForge 与 Fabric 约定的命名(NeoForge 21.1.249 有 532 个 {@code data/c/tags/...}),
     * 从高版本移植过来的数据包可能填的是这一支。两者<b>任一命中即可</b>, 因此本模组在
     * {@code data/forge/tags/items/tools/hammer.json} 与 {@code data/c/tags/items/tools/hammer.json} 里都
     * 声明了该标签(出厂为空 —— 1.20.1 没有原版锤子), 其它模组把锤子加入任一支即可共用这条路径。</p>
     */
    private static final TagKey<Item> HAMMER_TOOLS_FORGE =
        TagKey.create(Registries.ITEM, new ResourceLocation("forge", "tools/hammer"));

    /** 1.20.1 的兼容分支, 命名依据见 {@link #HAMMER_TOOLS_FORGE} 的说明。 */
    private static final TagKey<Item> HAMMER_TOOLS_COMMON =
        TagKey.create(Registries.ITEM, new ResourceLocation("c", "tools/hammer"));

    /**
     * 一次锻击后给锤子加的冷却(tick) —— 与"一次右击只做一件"的节奏配套, 防止连点。
     * 出厂默认值; 运行期以配置项 {@code process.forging_cooldown} 为准。
     */
    static final int DEFAULT_COOLDOWN_TICKS = com.nonono.createbetterwrench.config.WrenchConfig.DEFAULT_FORGING_COOLDOWN;

    /** 当前配置的锻造冷却(tick); 0 表示不加冷却。 */
    static int cooldownTicks() {
        return com.nonono.createbetterwrench.config.WrenchConfig.forgingCooldownTicks();
    }

    private Forging() {
    }

    // ---------------------------------------------------------------- 置物台: 冲压(create:pressing)

    /**
     * 对已锁定的置物台执行一次冲压: 台面物品 + {@code create:pressing} 配方。
     *
     * <p>只处理台面上的<b>一个</b>物品(压板配方本身就是单输入), 产出直接弹出; 没有配准则返回
     * {@code false}(交给调用方继续尝试其它加工路径, 因为"台面物品不是冲压目标"并不是一种错误)。</p>
     */
    public static boolean tryOnDepot(Level level, BlockPos pos, DepotBlockEntity depot, Player player,
                                     ItemStack held, InteractionHand hand, ItemStack current) {
        if (!isHammer(held) || onCooldown(player, held))
            return false;

        // 1.20.1: Create 的 find(C extends Container, ...) 与本地的容器类型推断不合,
        // 改用原版配方管理器按类型查找(1.20.1 返回配方本体, 没有 RecipeHolder)
        Optional<PressingRecipe> found = AllRecipeTypes.PRESSING.find(
            new net.minecraftforge.items.wrapper.RecipeWrapper(
                new net.minecraftforge.items.wrapper.InvWrapper(
                    new net.minecraft.world.SimpleContainer(current.copyWithCount(1)))),
            level);
        if (found.isEmpty())
            return false;
        if (AssembleLogic.blockedSubKind(player, ProcessKind.FORGING))
            return true;

        ItemStack product = firstNonEmpty(found.get().rollResults());
        if (product.isEmpty())
            return false;

        // 台面只少 1 个; 恰好取完时按既定约定立刻从原料堆续下一个(台面不空)
        if (current.getCount() > 1)
            AssembleLogic.setDepot(depot, current.copyWithCount(current.getCount() - 1));
        else
            AssembleLogic.consumeAndRefill(level, pos, depot);

        AssembleLogic.giveToPlayer(player, product);
        afterStrike(level, pos, player, held, hand);
        return true;
    }

    // ---------------------------------------------------------------- 公用

    /**
     * 手持物是不是锤类: 命中 {@code forge:tools/hammer} 或 {@code c:tools/hammer} 任一即可
     * (1.20.1 上没有原版锤子, 该标签完全由其它模组或数据包填充; 两个标签都不存在/为空时恒为 false)。
     */
    static boolean isHammer(ItemStack held) {
        return !held.isEmpty()
            && (held.is(HAMMER_TOOLS_FORGE) || held.is(HAMMER_TOOLS_COMMON));
    }

    private static boolean onCooldown(Player player, ItemStack held) {
        return player.getCooldowns().isOnCooldown(held.getItem());
    }

    /** 一次成功锻击的代价与反馈: 锤子扣 1 点耐久 + 5 tick 冷却 + 铁砧音与火花粒子。 */
    private static void afterStrike(Level level, BlockPos pos, Player player, ItemStack held, InteractionHand hand) {
        AssembleLogic.consumeHeld(player, held, hand, false);
        int cooldown = cooldownTicks();
        if (cooldown > 0)
            player.getCooldowns().addCooldown(held.getItem(), cooldown);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5f,
            level.random.nextFloat() * 0.2f + 1.1f);
        if (level instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.05,
                pos.getZ() + 0.5, 6, 0.2, 0.05, 0.2, 0.1);
    }

    /** 取掷出的第一件非空产出(冲压的中介物与成品都只有一个产出槽)。 */
    private static ItemStack firstNonEmpty(List<ItemStack> results) {
        for (ItemStack stack : results)
            if (!stack.isEmpty())
                return stack.copy();
        return ItemStack.EMPTY;
    }
}
