package com.nonono.createbetterwrench.assemble;

import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/**
 * 「工作」(内部 id 仍为 {@code assemble})模式的服务端核心: 在**已锁定的置物台**上,
 * 用手代替机器, 依次尝试四种原版机制(全部走 Create 自己的配方体系, 不硬编码具体配方):
 *
 * <ol>
 *   <li><b>序列装配</b> —— {@code create:sequenced_assembly}, 等价于发射器(Deployer)推进装配。</li>
 *   <li><b>机械手式施加</b> —— {@code create:deploying} 与 {@code create:item_application}。
 *       查找顺序与优先级完全照搬 {@code DeployerBlockEntity#getRecipe}。</li>
 *   <li><b>原木去皮</b> —— 原版 {@link AxeItem} 机制(Create 只在 JEI 里展示这条"隐式配方")。</li>
 *   <li><b>注液</b> —— {@code create:filling}, 等价于注液器(Spout)。</li>
 * </ol>
 *
 * <h2>三个位置</h2>
 * 一个置物台只能渲染一个物品堆, 所以台面**只放"正在加工"的那一个**, 另外两个位置由
 * {@link DepotPiles} 用掉落物实体摆在**置物台的两个对角**(带正常重力, 会自己落在台面上):
 * <pre>
 *   原料堆(RAW, 西北角, 锁定期间不可拿)   ←  台面中央(正在加工)  →  成品堆(DONE, 东南角, 可拿)
 * </pre>
 *
 * <p><b>自动续料</b>: 每加工完一件, {@link #consumeAndRefill} 会在**同一个 tick 内**把原料堆的下一个
 * 顶上台面, 所以台面在原料堆还有货时不会空着, 玩家加工完一件就能直接接着下一件
 * (不需要先"右键放料"再"右键加工")。装配**失败**的产物直接弹成普通掉落物, 不进成品堆。</p>
 */
public final class AssembleLogic {

    private AssembleLogic() {
    }

    public static boolean tryAssemble(Level level, BlockPos pos, DepotBlockEntity depot,
                                      Player player, ItemStack held, InteractionHand hand) {
        if (held.isEmpty() || held.is(BetterWrenchMod.BETTER_WRENCH))
            return false; // 空手/扳手不参与工作模式(扳手用于锁定/解锁)

        // 台面空了就先从原料堆续一个上来。
        // 正常情况下 consumeAndRefill 已经在每次加工结束时续好了, 这里只是兜底
        // (服务器重启/存档读入后台面可能是空的, 而原料堆还在)。
        refillIfEmpty(level, pos, depot);
        ItemStack current = depot.getHeldItem();
        if (current.isEmpty())
            return false;

        if (trySequencedAssembly(level, pos, depot, player, held, hand, current))
            return true;
        if (tryApplyingRecipe(level, pos, depot, player, held, hand, current))
            return true;
        if (tryStrippingLog(level, pos, depot, player, held, hand, current))
            return true;
        if (trySpoutFilling(level, pos, depot, player, held, hand, current))
            return true;
        return false;
    }

    // ---------------------------------------------------------------- 1. 序列装配

    private static boolean trySequencedAssembly(Level level, BlockPos pos, DepotBlockEntity depot,
                                                Player player, ItemStack held, InteractionHand hand,
                                                ItemStack current) {
        Optional<RecipeHolder<DeployerApplicationRecipe>> found = SequencedAssemblyRecipe.getRecipe(
            level, current, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class);
        if (found.isEmpty())
            return false;
        DeployerApplicationRecipe recipe = found.get().value();
        if (!recipe.getRequiredHeldItem().test(held))
            return false;

        ItemStack working = current.copyWithCount(1);
        splitExtras(level, pos, depot); // 多余的先挪到原料堆, 台面只留正在加工的那一个

        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, working, recipe, true);
        consumeHeld(player, held, hand, recipe.shouldKeepHeldItem());
        dropExtras(level, pos, results);

        ItemStack out = results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
        if (out.isEmpty()) {
            consumeAndRefill(level, pos, depot);
            return true;
        }

        // 还能继续推进 => 仍是中间产物, 留在中间
        boolean canContinue = SequencedAssemblyRecipe
            .getRecipe(level, out, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class)
            .isPresent();
        if (canContinue) {
            setDepot(depot, out);
            playPickup(level, pos);
            return true;
        }

        // 序列结束: **成品先在台面上停留若干 tick(见 DepotProductEjector.STAY_TICKS), 然后弹出**(用户指定的观感)。
        //
        // ℹ️ 这里**刻意不再区分**成品与废料。旧代码拿 SequencedAssemblyRecipe.resultPool.getFirst()
        //    当"唯一的目标成品"去比对 out, 但结果池实际上是**按权重随机抽取**的
        //    (SequencedAssemblyRecipe.rollResult, 132-144 行), 不存在唯一成品 —— 那个判断本身就不成立,
        //    于是成品会被误判成废料弹出。用户明确说"所有成品都被弹出"这个效果非常好、要保留,
        //    所以现在一律走弹出, 反而变成确定性的了。
        holdThenEject(level, pos, depot, out, player);
        playPickup(level, pos);
        return true;
    }

    // ---------------------------------------------------------------- 2. 机械手式施加

    private static boolean tryApplyingRecipe(Level level, BlockPos pos, DepotBlockEntity depot,
                                             Player player, ItemStack held, InteractionHand hand,
                                             ItemStack current) {
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, current.copyWithCount(1));
        inv.setStackInSlot(1, held.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(inv);

        Optional<RecipeHolder<Recipe<RecipeWrapper>>> found =
            AllRecipeTypes.DEPLOYING.find(wrapper, level).filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        if (found.isEmpty())
            found = AllRecipeTypes.ITEM_APPLICATION.find(wrapper, level).filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        if (found.isEmpty())
            return false;

        Recipe<RecipeWrapper> recipe = found.get().value();
        ItemStack working = current.copyWithCount(1);
        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, working, recipe, true);
        if (results.isEmpty() || results.get(0).isEmpty())
            return false;

        boolean keepHeld = recipe instanceof ItemApplicationRecipe application && application.shouldKeepHeldItem();
        consumeHeld(player, held, hand, keepHeld);
        splitExtras(level, pos, depot);
        dropExtras(level, pos, results);

        holdThenEject(level, pos, depot, results.get(0).copy(), player);
        playPickup(level, pos);
        return true;
    }

    // ---------------------------------------------------------------- 3. 原木去皮

    private static boolean tryStrippingLog(Level level, BlockPos pos, DepotBlockEntity depot,
                                           Player player, ItemStack held, InteractionHand hand,
                                           ItemStack current) {
        if (!held.is(ItemTags.AXES))
            return false;
        if (!(current.getItem() instanceof BlockItem blockItem))
            return false;

        BlockState stripped = AxeItem.getAxeStrippingState(blockItem.getBlock().defaultBlockState());
        if (stripped == null)
            return false;
        ItemStack out = new ItemStack(stripped.getBlock().asItem());
        if (out.isEmpty())
            return false;

        if (!player.isCreative() && held.getMaxDamage() > 0)
            held.hurtAndBreak(1, player, handSlot(hand));

        splitExtras(level, pos, depot);
        holdThenEject(level, pos, depot, out, player);
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    // ---------------------------------------------------------------- 4. 注液

    /**
     * 等价于注液器, 但**按流体实量批量注**:
     * 一份配方可能只吃 25mB(例如发光石), 而玩家手里一个桶是 1000mB,
     * 所以一次操作会把 `1000 / 单份用量` 个物品一起注满(不超过实际可用的输入数量)。
     */
    private static boolean trySpoutFilling(Level level, BlockPos pos, DepotBlockEntity depot,
                                           Player player, ItemStack held, InteractionHand hand,
                                           ItemStack current) {
        if (!GenericItemEmptying.canItemBeEmptied(level, held))
            return false;

        FluidStack available = GenericItemEmptying.emptyItem(level, held.copy(), true).getFirst();
        if (available.isEmpty())
            return false;

        ItemStack probe = current.copyWithCount(1);
        if (!FillingBySpout.canItemBeFilled(level, probe))
            return false;
        int perItem = FillingBySpout.getRequiredAmountForItem(level, probe, available);
        if (perItem <= 0)
            return false;

        // 桶里这 1000mB 够注几份
        int units = available.getAmount() / perItem;
        if (units <= 0)
            return false;

        // 输入 = 台面现有的 + 从原料堆续上来的, 最多凑到 units 个
        int onDepot = current.getCount();
        int wanted = Math.min(units, onDepot + (int) Math.min(Integer.MAX_VALUE, countRaw(level, pos)));
        ItemStack combined = current.copy();
        if (wanted > onDepot) {
            ItemStack extra = DepotPiles.take(level, pos, DepotPiles.RAW, wanted - onDepot);
            if (!extra.isEmpty())
                combined.grow(extra.getCount());
        }
        int toFill = Math.min(units, combined.getCount());
        if (toFill <= 0)
            return false;

        int made = 0;
        for (int i = 0; i < toFill; i++) {
            ItemStack filled = FillingBySpout.fillItem(level, perItem, combined.copyWithCount(1), available.copy());
            if (filled.isEmpty())
                break;
            DepotPiles.deposit(level, pos, DepotPiles.DONE, filled);
            made++;
        }
        if (made <= 0)
            return false;

        // 真正抽掉玩家手里那份流体, 并把空容器还给他
        if (!player.isCreative()) {
            Pair<FluidStack, ItemStack> drained = GenericItemEmptying.emptyItem(level, held.copy(), false);
            ItemStack container = drained.getSecond();
            if (held.getCount() <= 1) {
                player.setItemInHand(hand, container.isEmpty() ? ItemStack.EMPTY : container);
            } else {
                held.shrink(1);
                if (!container.isEmpty() && !player.getInventory().add(container))
                    player.drop(container, false);
            }
        }

        // 没注到的输入退回原料堆, 台面清空后续下一个
        int leftover = combined.getCount() - made;
        if (leftover > 0)
            DepotPiles.deposit(level, pos, DepotPiles.RAW, combined.copyWithCount(leftover));
        consumeAndRefill(level, pos, depot);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    private static long countRaw(Level level, BlockPos pos) {
        // 只要一个上界; 真正的取出由 DepotPiles.take 完成
        return DepotPiles.hasAny(level, pos, DepotPiles.RAW) ? 64L : 0L;
    }

    // ---------------------------------------------------------------- 台面 / 料堆

    /** 台面物品多于 1 个时, 把多余的挪到原料堆; 台面只留 1 个(正在加工的那个)。 */
    private static void splitExtras(Level level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack current = depot.getHeldItem();
        if (current.getCount() <= 1)
            return;
        ItemStack extras = current.copyWithCount(current.getCount() - 1);
        setDepot(depot, current.copyWithCount(1));
        DepotPiles.deposit(level, pos, DepotPiles.RAW, extras);
    }

    /**
     * 「成品先在台面上停留若干 tick、然后弹出」的入口。
     *
     * <p>停留时长由**该玩家**在「加工」模式里 Ctrl+滚轮选的档位决定
     * ({@link com.nonono.createbetterwrench.mode.AssembleStay} → {@link DepotStayState},
     * 客户端发包同步;没同步过就用默认档「中」= 4 tick)。</p>
     *
     * <p>先把成品摆上台面({@code notifyUpdate} 过, 客户端真的看得见), 再由
     * {@link DepotProductEjector} 在停留时间到点后调 {@link #ejectHeldAndRefill}
     * 把它弹出去、并清空台面 + 自动续料。</p>
     *
     * <p>⚠️ 这里**不**立刻续料 —— 续料发生在弹出那一刻, 为的就是让成品在台面上"停一下"。</p>
     */
    private static void holdThenEject(Level level, BlockPos pos, DepotBlockEntity depot,
                                      ItemStack product, Player player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        DepotProductEjector.holdThenEject(serverLevel, pos, depot, product,
            DepotStayState.get(player.getUUID()));
    }

    /**
     * 停留时间到: 把台面上那件成品**弹出**, 然后自动续上原料堆的下一个。
     * 只由 {@link DepotProductEjector} 调用。
     */
    static void ejectHeldAndRefill(ServerLevel level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty())
            return;
        DepotPiles.eject(level, pos, held.copy()); // 弹出去(带向上初速的成品堆掉落物)
        consumeAndRefill(level, pos, depot);       // 台面清空 + 立刻续下一个原料
    }

    /**
     * 解锁置物台时: 把**台面上正在加工的那一个**和**两个料堆(原料堆 + 成品堆, 含被弹出去的那些)**
     * 全部**返还到玩家背包**。
     *
     * <p>用户要求(2026-09-17):「在解锁置物台后, 把所有置物台上面的东西都返还到玩家背包(包括掉落物堆)」。</p>
     *
     * <p>装不下的部分由原版 {@code Inventory.placeItemBackInInventory} 负责掉在玩家脚下, **不会凭空消失**。</p>
     */
    public static void returnHeldAndPiles(ServerLevel level, BlockPos pos, DepotBlockEntity depot, ServerPlayer player) {
        // ① 台面上的那一个
        ItemStack held = depot.getHeldItem();
        if (!held.isEmpty()) {
            setDepot(depot, ItemStack.EMPTY);
            give(player, held);
        }
        // ② 两个料堆(原料堆 + 成品堆)
        for (ItemStack stack : DepotPiles.drainAll(level, pos))
            give(player, stack);
    }

    /** 塞进背包; 装不下的由 placeItemBackInInventory 掉在玩家脚下。 */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty())
            return;
        player.getInventory().placeItemBackInInventory(stack.copy());
    }

    /**
     * 「自动续料」: 台面若空就从原料堆补 1 个上去。
     *
     * <p>供外部时机调用(例如刚刚锁定置物台时、以及每次加工入口兜底)。</p>
     *
     * @return 是否真的续上了
     */
    public static boolean refillIfEmpty(Level level, BlockPos pos, DepotBlockEntity depot) {
        if (!depot.getHeldItem().isEmpty())
            return false;
        ItemStack next = DepotPiles.take(level, pos, DepotPiles.RAW, 1);
        if (next.isEmpty())
            return false;
        setDepot(depot, next);
        return true;
    }

    /**
     * 消耗掉台面正在加工的那一个, 并**在同一 tick 内立刻续上原料堆的下一个**。
     *
     * <p>这就是「[加工] 的台面不会空着」的实现点: 台面一空就补料,
     * 于是玩家加工完一件就能直接接着下一件, 不用先"右键放料"再"右键加工"。</p>
     *
     * <p><b>⚠️ 续料还顺手解决了一个顺序问题, 别把这两步拆开:</b>
     * 被弹出的成品是以掉落物实体形式在台面上方生成的, 要过几 tick 才落地。
     * 如果此时台面是空的, 它落地就会被置物台收进去;
     * 而本方法先把下一个原料顶上台面, 落地时台面是**占用**状态,
     * 普通置物台在占用时拒收掉落物({@link com.simibubi.create.content.logistics.depot.DepotBehaviour}
     * 的 `isOccupied()`) ⇒ 成品会稳稳停在台面上成为成品堆。
     * 只有原料堆也空了(一个批次加工完)才会被收进去, 那时正好也该收工了。</p>
     */
    private static void consumeAndRefill(Level level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack next = DepotPiles.take(level, pos, DepotPiles.RAW, 1);
        // next 可能为空: 那就是单纯把台面清空
        setDepot(depot, next);
    }

    /** 把某件物品摆上台面(会 notifyUpdate, 客户端才看得见)。包内共享给 {@link DepotProductEjector}。 */
    static void setDepot(DepotBlockEntity depot, ItemStack stack) {
        depot.setHeldItem(stack);
        // 关键: DepotBlockEntity.setHeldItem 不会自行同步客户端(Create 自己的调用方都会补 notifyUpdate),
        // 不 notify 的话客户端会一直渲染旧物品。
        depot.notifyUpdate();
    }

    private static void dropExtras(Level level, BlockPos pos, List<ItemStack> results) {
        for (int i = 1; i < results.size(); i++)
            if (!results.get(i).isEmpty())
                Block.popResource(level, pos.above(), results.get(i));
    }

    private static void playPickup(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, 0.75f);
    }

    // ---------------------------------------------------------------- 手持物品处理

    private static void consumeHeld(Player player, ItemStack held, InteractionHand hand, boolean keepHeld) {
        if (player.isCreative() || keepHeld)
            return;
        if (held.getMaxDamage() > 0) {
            held.hurtAndBreak(1, player, handSlot(hand));
            return;
        }
        ItemStack leftover = held.getCraftingRemainingItem();
        held.shrink(1);
        if (held.isEmpty())
            player.setItemInHand(hand, leftover);
        else if (!leftover.isEmpty() && !player.getInventory().add(leftover))
            player.drop(leftover, false);
    }

    private static EquipmentSlot handSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
