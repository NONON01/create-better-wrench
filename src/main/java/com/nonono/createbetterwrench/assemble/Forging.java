package com.nonono.createbetterwrench.assemble;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.mode.ProcessKind;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.item.SmartInventory;

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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「加工」模式里的<b>锻造</b>路径 —— 用手里的锤子代替动力冲压机, 直接执行机械动力的冲压配方。
 *
 * <h2>两个落点, 对应冲压机的两种工作方式</h2>
 * <table border="1">
 *   <caption>与 Create 的对应关系</caption>
 *   <tr><th>落点</th><th>配方类型</th><th>Create 中文名</th><th>输入</th></tr>
 *   <tr><td>已锁定的<b>置物台</b></td><td>{@code create:pressing}</td><td>冲压</td><td>单个物品</td></tr>
 *   <tr><td>已锁定的<b>工作盆</b></td><td>{@code create:compacting}</td><td>压缩</td><td>多个物品</td></tr>
 * </table>
 *
 * <p>两种落点都直接使用 Create 自己的配方数据(<b>不硬编码任何配方</b>), 因此原版与其它模组新增的
 * 冲压 / 压缩配方自动生效。第一类的判据取自 {@code AllRecipeTypes.PRESSING}(与动力冲压机处理单个物品时
 * 同一份数据), 第二类取自 {@code AllRecipeTypes.COMPACTING}(与动力冲压机配合工作盆时同一份数据),
 * 施加也交给 {@link BasinRecipe#match} 与 {@link BasinRecipe#apply}, 不自己实现配方的扣料与产出结算。</p>
 *
 * <h2>三条设计约定</h2>
 * <ol>
 *   <li><b>锤类物品触发</b>: 手持物在 {@code c:tools/hammer} 标签里即可, 本模组在数据包里把原版重锤
 *       ({@code minecraft:mace})加入该标签, 其它模组的锤子加入同一标签也能用。不新增物品与贴图;</li>
 *   <li><b>产物直接弹出</b>: 冲压产出以普通掉落物落在锚点旁, 不做"停留后弹出", 也不发任何 actionbar 提示;</li>
 *   <li><b>没有配方就什么也不发生</b>: 不消耗材料、不扣耐久、不提示(与其它路径"给出提示"的风格不同,
 *       这是本路径明确的设计约定)。</li>
 * </ol>
 *
 * <h2>已知简化</h2>
 * <p>未检查工作盆的<b>热量</b>: Create 自带的压缩配方全部 {@code heatRequirement = none}, 因此现阶段不
 * 影响既有配方; 若后续要支持需要加热的模组配方, 可在本类里补一次热量判据
 * ({@code BasinBlockEntity#getHeatLevelOf} 为静态方法, 可直接读取工作盆下方的方块状态)。</p>
 */
public final class Forging {

    /**
     * 「锻造」认的锤类标签。本模组在 {@code data/c/tags/item/tools/hammer.json} 里把原版重锤加入其中,
     * 其它模组只要把自己的锤子加入同一标签即可共用这条路径。
     */
    private static final TagKey<Item> HAMMER_TOOLS =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/hammer"));

    /** 一次锻击后给锤子加的冷却(tick) —— 与"一次右击只做一件/一批"的节奏配套, 防止连点。 */
    static final int COOLDOWN_TICKS = 5;

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

        Optional<RecipeHolder<PressingRecipe>> found =
            AllRecipeTypes.PRESSING.find(new SingleRecipeInput(current.copyWithCount(1)), level);
        if (found.isEmpty())
            return false;
        if (AssembleLogic.blockedSubKind(player, ProcessKind.FORGING))
            return true;

        ItemStack product = firstNonEmpty(found.get().value().rollResults(level.random));
        if (product.isEmpty())
            return false;

        // 台面只少 1 个; 恰好取完时按既定约定立刻从原料堆续下一个(台面不空)
        if (current.getCount() > 1)
            AssembleLogic.setDepot(depot, current.copyWithCount(current.getCount() - 1));
        else
            AssembleLogic.consumeAndRefill(level, pos, depot);

        AssembleLogic.dropProduct(level, AssembleLogic.productDropPos(level, pos), product, true);
        afterStrike(level, pos, player, held, hand);
        return true;
    }

    // ---------------------------------------------------------------- 工作盆: 压缩(create:compacting)

    /**
     * 对已锁定的工作盆执行一次压缩: 盆内物品 + {@code create:compacting} 配方。
     *
     * <p>判据与施加完全交给 {@link BasinRecipe#match} 与 {@link BasinRecipe#apply}, 因此流体输入、
     * 容器返还等结算与动力冲压机完全一致。施加完成后把工作盆<b>输出槽里的物品</b>全部弹出(本路径的
     * 约定是"产物直接弹出"), 流体产出仍留在工作盆的输出储罐里。</p>
     */
    public static boolean tryOnBasin(Level level, BlockPos pos, BasinBlockEntity basin, Player player,
                                     ItemStack held, InteractionHand hand) {
        if (!isHammer(held) || onCooldown(player, held))
            return false;

        Recipe<?> match = findCompactingRecipe(level, basin);
        if (match == null)
            return false;
        if (AssembleLogic.blockedSubKind(player, ProcessKind.FORGING))
            return true;
        if (!BasinRecipe.apply(basin, match))
            return false;

        ejectOutputs(level, pos, basin);
        afterStrike(level, pos, player, held, hand);
        return true;
    }

    /** 在 {@code create:compacting} 里找第一条与工作盆内容物匹配的配方; 没有则返回 {@code null}。 */
    private static Recipe<?> findCompactingRecipe(Level level, BasinBlockEntity basin) {
        List<RecipeHolder<CompactingRecipe>> all =
            level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.COMPACTING.getType());
        for (RecipeHolder<CompactingRecipe> holder : all)
            if (BasinRecipe.match(basin, holder.value()))
                return holder.value();
        return null;
    }

    /** 把工作盆输出槽里的物品全部弹出(留在盆里的话, 工作盆会尝试向下输出或被漏斗取走, 与本路径的约定不符)。 */
    private static void ejectOutputs(Level level, BlockPos pos, BasinBlockEntity basin) {
        SmartInventory out = basin.getOutputInventory();
        List<ItemStack> drained = new ArrayList<>();
        for (int slot = 0; slot < out.getSlots(); slot++) {
            ItemStack stack = out.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty())
                drained.add(stack);
        }
        Vec3 anchor = AssembleLogic.productDropPos(level, pos);
        for (ItemStack stack : drained)
            AssembleLogic.dropProduct(level, anchor, stack, true);
    }

    // ---------------------------------------------------------------- 公用

    /** 手持物是不是锤类(标签 {@code c:tools/hammer})。 */
    static boolean isHammer(ItemStack held) {
        return !held.isEmpty() && held.is(HAMMER_TOOLS);
    }

    private static boolean onCooldown(Player player, ItemStack held) {
        return player.getCooldowns().isOnCooldown(held.getItem());
    }

    /** 一次成功锻击的代价与反馈: 锤子扣 1 点耐久 + 5 tick 冷却 + 铁砧音与火花粒子。 */
    private static void afterStrike(Level level, BlockPos pos, Player player, ItemStack held, InteractionHand hand) {
        AssembleLogic.consumeHeld(player, held, hand, false);
        player.getCooldowns().addCooldown(held.getItem(), COOLDOWN_TICKS);
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
