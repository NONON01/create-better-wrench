package com.nonono.createbetterwrench.assemble;

import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.joml.Vector3f;

/**
 * 「工作」(内部 id 仍为 {@code assemble})模式的服务端核心: 在**已锁定的置物台**上,
 * 用手代替机器, 依次尝试五种机制(全部走 Create/原版自己的配方体系, 不硬编码具体配方):
 *
 * <ol>
 *   <li><b>序列装配</b> —— {@code create:sequenced_assembly}, 等价于发射器(Deployer)推进装配。</li>
 *   <li><b>机械手式施加</b> —— {@code create:deploying} 与 {@code create:item_application}。
 *       查找顺序与优先级完全照搬 {@code DeployerBlockEntity#getRecipe}。</li>
 *   <li><b>原木去皮</b> —— 原版 {@link AxeItem} 机制(Create 只在 JEI 里展示这条"隐式配方")。</li>
 *   <li><b>注液</b> —— {@code create:filling}, 等价于注液器(Spout)。</li>
 *   <li><b>批量鼓风处理</b>(2026-09-20 新增) —— 模仿 Create 鼓风机: 水桶 ⇒ 洗涤({@code create:splashing})、
 *       岩浆桶 ⇒ 冶炼(熔炉/高炉)、打火石 ⇒ 烟熏; 若置物台**下方**是灵魂沙/灵魂土/灵魂火则 ⇒ 缠魂
 *       ({@code create:haunting}), 缠魂没配方时**回退烟熏**。一次把**一整摞**物品完全转换
 *       (台面那一摞 + 原料堆里的同类, 上限见配置 {@code assemble.fan_batch_limit}), 产出**全部弹出**。
 *       见 {@link #tryFanProcessing}。</li>
 * </ol>
 *
 * <h2>两个位置</h2>
 * 一个置物台只能渲染一个物品堆, 所以台面**只放"正在加工"的那一个**, 原料则由
 * {@link DepotPiles} 用掉落物实体摆在**置物台的西北角**(带正常重力, 会自己落在台面上):
 * <pre>
 *   原料堆(RAW, 西北角, 锁定期间不可拿)   ←  台面中央(正在加工)
 * </pre>
 *
 * <p><b>已经没有「成品堆」这个概念了</b>: 加工产出(含注液批量产出)一律作为**普通掉落物**
 * 直接落在世界上 —— 不打任何持久化标记、不 {@code setUnlimitedLifetime()}, 因此会像普通掉落物
 * 一样被拾取、也会正常消失。解锁置物台时只返还「台面上那一个 + 原料堆」。</p>
 *
 * <p><b>自动续料</b>: 每加工完一件, {@link #consumeAndRefill} 会在**同一个 tick 内**把原料堆的下一个
 * 顶上台面, 所以台面在原料堆还有货时不会空着, 玩家加工完一件就能直接接着下一件
 * (不需要先"右键放料"再"右键加工")。装配**失败**的产物直接弹成普通掉落物。</p>
 */
public final class AssembleLogic {

    /**
     * 注液批量产出的落点相对置物台中心的水平偏移(东南侧)。
     * 与原料堆(西北角)分开, 免得产出和原料混在一处。
     *
     * <p>⚠️ 复审 B-17: 直接引用 {@link DepotPiles#CORNER_OFFSET} 而不是另写一个 0.27 ——
     * 这两处必须**同值**(一西一东的对角对称), 分开写迟早会改一处忘另一处。</p>
     */
    private static final double PRODUCT_DROP_OFFSET = DepotPiles.CORNER_OFFSET;

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
        // ⚠️ 必须排在 trySpoutFilling **之后**: 岩浆桶 → 烈焰蛋糕那条注液路径是已实机验证过的功能,
        // 放前面会把水桶/岩浆桶的注液用途整个抢掉(鼓风处理会先判 canProcess)。
        if (tryFanProcessing(level, pos, depot, player, held, hand, current))
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

        // 序列结束: **成品先在台面上停留若干 tick, 然后弹出**(用户指定的观感; 时长见 DepotStayState)。
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
        int extraMerged = 0;   // 实际并入 combined 的"从原料堆取来"的数量(失败时要原样退回)
        if (wanted > onDepot) {
            ItemStack extra = DepotPiles.take(level, pos, wanted - onDepot);
            if (!extra.isEmpty()) {
                if (ItemStack.isSameItemSameComponents(combined, extra)) {
                    extraMerged = extra.getCount();
                    combined.grow(extraMerged);
                } else {
                    // 异类型(不该发生): 原样放回原料堆, 绝不留在手上丢掉
                    DepotPiles.deposit(level, pos, extra);
                }
            }
        }
        int toFill = Math.min(units, combined.getCount());
        if (toFill <= 0)
            return false;

        int made = 0;
        for (int i = 0; i < toFill && !combined.isEmpty(); i++) {
            ItemStack filled = FillingBySpout.fillItem(level, perItem, combined.copyWithCount(1), available.copy());
            if (filled.isEmpty())
                break;
            // 产出是**普通掉落物**(已无成品堆): 落在置物台**东南侧**的产出收集点, 无初速
            dropProduct(level, productDropPos(pos), filled.copy(), false);
            // 审计 B-6: 显式扣减这一轮的输入, 不再靠"combined.getCount() - made"算术对消
            combined.shrink(1);
            made++;
        }
        if (made <= 0) {
            // ⚠️ 一件都没注成: 必须把**从原料堆取来的那部分输入原样退回**。
            //    这些物品已经离开料堆实体、只存在于 combined 里, 直接 return 就**静默丢了**。
            //    (台面那部分不用管 —— 它还在置物台上, 我们没动它。)
            if (extraMerged > 0)
                DepotPiles.deposit(level, pos, combined.copyWithCount(extraMerged));
            return false;
        }

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
        if (!combined.isEmpty())
            DepotPiles.deposit(level, pos, combined.copy());
        consumeAndRefill(level, pos, depot);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    // ---------------------------------------------------------------- 5. 鼓风处理(批量)

    /**
     * 第 5 条路径: 用**水桶 / 岩浆桶 / 打火石**模拟 Create 鼓风机的一次性处理,
     * 但对**一整摞**物品一次性完成 —— 洗涤({@code create:splashing})、冶炼(原版熔炉/高炉)、
     * 烟熏(原版烟熏炉)、缠魂({@code create:haunting})。
     *
     * <p>三种手持物的含义(候选类型按序尝试):</p>
     * <ul>
     *   <li>{@link Items#WATER_BUCKET} → 洗涤({@code SPLASHING});</li>
     *   <li>{@link Items#LAVA_BUCKET} → 冶炼({@code BLASTING});</li>
     *   <li>{@link Items#FLINT_AND_STEEL} → 置物台**下方**是灵魂沙 / 灵魂土 / 灵魂火时**先试缠魂**
     *       ({@code HAUNTING}), 缠魂没配方(例如台面是食物)再**回退烟熏**({@code SMOKING});
     *       下方不是灵魂底座时只试烟熏。</li>
     * </ul>
     *
     * <p><b>一次转换多少:</b> 台面那一摞 + 原料堆里的**同类**物品, 合计上限 = **该物品的最大堆叠数**
     * (置物台本身的理论上限, 原版即 64)。也就是"一次右击 = 一整摞"。原料堆取来的部分只在与台面物品**物品+组件完全一致**时才并入,
     * 且**加工失败时原样退回原料堆**(与 {@code trySpoutFilling} 的做法一致, 绝不静默吞料)。</p>
     *
     * <p><b>为什么在 filling 之后:</b> 注液(岩浆桶 → 烈焰蛋糕等)是已实机验证过的路径,
     * 必须先给它机会; 本路径只作为"注液没命中"时的兜底。详见 {@link #tryAssemble} 的调用点注释。</p>
     *
     * <p><b>⚠️ 与 Create 的语义差异(很重要):</b> 上游 {@code FanProcessingType#process} 返回
     * <b>null</b> 或 <b>空列表</b> 时, Create 的 {@code FanProcessing.applyProcessing} 会
     * {@code entity.discard()} —— 也就是**销毁物品**(例如被岩浆烧掉的非防火物品)。
     * 本模组**刻意反过来**: 一律当作"不适用", **保持台面物品原样、绝不销毁玩家物品**。</p>
     *
     * <p><b>消耗:</b> 水桶 / 岩浆桶**完全不消耗**(也不给空桶); 打火石只走
     * {@link #consumeHeld} 的耐久分支 —— 无论一次转换多少个, 都只扣 **1 点耐久**。</p>
     *
     * <p><b>产出:</b> 整批交给 {@code process} 一次即代表"整批完全转换"
     * ({@code RecipeApplier.applyRecipeOn} 内部按 {@code getCount()} 逐份掷结果, 并把同类产出
     * 合并成尽量满的堆叠)。弹出策略按**本次结果种类数**二分(2026-09-22 用户规则):
     * <b>多种产品 ⇒ 全部直接弹出</b>(台面只有一个位置, 多产品留不住);
     * <b>单一产品 ⇒ 稍作停留</b> —— 摆上台面, 按 Ctrl+滚轮**停留档位**(0/2/4/8 tick)到点再弹,
     * 并在弹出那一刻从原料堆续下一份原料(与其它四条路径完全一致;「不停留」档即等于立刻弹)。</p>
     *
     * <p><b>反馈(2026-09-20 追加, 用户要求):</b> 成功时播**原版音效** —— 水桶 ⇒ {@code BUCKET_EMPTY}(倒水)、
     * 岩浆桶 ⇒ {@code BUCKET_EMPTY_LAVA}、打火石 ⇒ {@code FLINTANDSTEEL_USE}; 同时喷洒与 Create 鼓风机
     * **同款**的粒子(洗涤 = 蓝色尘 + {@code SPIT}, 冶炼 = {@code LARGE_SMOKE}, 烟熏 = {@code POOF},
     * 缠魂 = {@code SOUL_FIRE_FLAME} + {@code SMOKE})。详见 {@link #playFanFeedback}。</p>
     */
    private static boolean tryFanProcessing(Level level, BlockPos pos, DepotBlockEntity depot,
                                            Player player, ItemStack held, InteractionHand hand,
                                            ItemStack current) {
        if (level.isClientSide)
            return false;

        List<FanProcessingType> candidates = fanTypesFor(level, pos, held);
        if (candidates.isEmpty())
            return false;

        // ① 先只拿**台面那个**探配方(canProcess 与数量无关) —— 这样"没配方"时不会白动原料堆。
        FanProcessingType type = null;
        for (FanProcessingType candidate : candidates) {
            if (candidate.canProcess(current, level)) {
                type = candidate;
                break;
            }
        }
        // 所有候选都不适用: 不消耗、不提示, 让别的路径继续尝试(与改动前一致)
        if (type == null)
            return false;

        // ② 并入**原料堆**里的同类物品, 合计凑到**置物台本身的理论上限**(= 该物品的最大堆叠数, 原版即 64)。
        //    原料堆里的异类型物品**原样放回**(与 trySpoutFilling 同样的防丢料处理)。
        //    ℹ️ 2026-09-22: 这里原来读配置项 `assemble.fan_batch_limit` —— 用户指出"置物台本身上限就是 64",
        //       没必要做成配置 ⇒ 已删除该配置项, 直接取物品自己的最大堆叠数。
        int limit = Math.max(1, current.getMaxStackSize());
        ItemStack batch = current.copy();
        // 台面那一摞若超过上限(把配置调小于台面数量): 只有 batch 这部分会被转换, 多出来的部分
        // **先留在这个局部变量里、不碰世界** —— 成功后才退回原料堆; 失败时台面原封不动。
        // ⚠️ 千万别在这里就 deposit: 那样"先退料、后失败"会让台面那一摞**重复一份**(退走的 + 台面上的)。
        ItemStack overflow = ItemStack.EMPTY;
        if (batch.getCount() > limit) {
            overflow = batch.copyWithCount(batch.getCount() - limit);
            batch = batch.copyWithCount(limit);
        }
        int fromPile = 0;
        if (batch.getCount() < limit) {
            ItemStack extra = DepotPiles.take(level, pos, limit - batch.getCount());
            if (!extra.isEmpty()) {
                if (ItemStack.isSameItemSameComponents(batch, extra)) {
                    fromPile = extra.getCount();
                    batch.grow(fromPile);
                } else {
                    DepotPiles.deposit(level, pos, extra);
                }
            }
        }

        // ③ process 可能返回 null(无配方)或空列表(不适用, 在 Create 里表示"销毁") —— 两者都按"不适用"处理。
        //    ⚠️ 还要挡住"列表非空、但元素全是空栈"的情形: 上游 {@code ItemHelper.multipliedOutput} 在产物为空时
        //    会无条件 add 一个 count=0 的栈(ItemHelper.java:49-59), 而 BlastingType.process 只判"配方是否存在"。
        //    只判 out.isEmpty() 的话, 这种批次会走"成功"分支被**静默丢掉**(不投物、无提示) ⇒ 要求至少一个非空产出。
        List<ItemStack> out = type.process(batch.copy(), level);
        boolean anyOutput = false;
        if (out != null)
            for (ItemStack stack : out)
                if (!stack.isEmpty()) {
                    anyOutput = true;
                    break;
                }
        if (!anyOutput) {
            // ⚠️ 失败时**必须把从原料堆取来的那部分原样退回** —— 那些物品已经离开料堆实体、
            //    只存在于 batch 里, 直接 return 就静默丢了。(台面那部分没动过, 不用管。)
            if (fromPile > 0)
                DepotPiles.deposit(level, pos, current.copyWithCount(fromPile));
            player.displayClientMessage(
                Component.translatable("msg." + BetterWrenchMod.MODID + ".assemble.fan_none"), true);
            return true; // 这次手势已被本模组消费: 保持台面原样
        }

        // ④ 产出弹出策略(2026-09-22 用户规则):
        //    · **多种产品** ⇒ **全部直接弹出**(台面只有一个位置, 多产品既留不住、也会互相卡位; 弹出后立刻续料);
        //    · **单一产品** ⇒ **稍作停留**: 摆上台面, 按加工模式 Ctrl+滚轮的**停留档位**(0/2/4/8 tick)到点再弹,
        //      续料由弹出那一刻的 ejectHeldAndRefill 完成 —— 与其它四条路径**完全一致**。
        //    ⚠️ 本次修订的来龙去脉: 上一轮按"直接弹出"实现时**绕过了 holdThenEject** ⇒ 用户实测发现"滚轮四档失效";
        //    但多产品又不能强行占台面, 于是按上面的规则分两种策略。判据用**本次实际结果的种类数**
        //    (非空栈数量): 概率性副产物这一批没掷出来时就按"单一产品"处理 ⇒ 稍作停留, 无副作用。
        //
        // ⚠️ 先处理"台面超出上限的那部分": 两条策略都会覆盖台面那一摞, 不在这里退回原料堆就是静默丢失。
        //    (失败分支不会走到这里, 所以它那时仍在台面上。)
        if (!overflow.isEmpty())
            DepotPiles.deposit(level, pos, overflow);

        Vec3 ejectFrom = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        ItemStack primary = ItemStack.EMPTY;
        int kinds = 0;
        for (ItemStack stack : out) {
            if (stack.isEmpty())
                continue;
            kinds++;
            if (primary.isEmpty())
                primary = stack.copy();
        }

        if (kinds > 1) {
            // 多种产品: 全部直接弹(主产出也不再单独留台面)
            for (ItemStack stack : out)
                if (!stack.isEmpty())
                    dropProduct(level, ejectFrom, stack.copy(), true);
            // 台面清空 + 立刻续料(顺序不能反: 台面被下一份原料占住, 弹出的产出落地才不会被吸回)
            consumeAndRefill(level, pos, depot);
        } else {
            // 单一产品(或万一为空): 稍作停留, 走档位
            if (primary.isEmpty())
                consumeAndRefill(level, pos, depot);
            else
                holdThenEject(level, pos, depot, primary, player);
        }
        playFanFeedback(level, pos, held, type);

        // 打火石: 只扣 1 点耐久(consumeHeld 内部: 创造模式 / keepHeld 直接返回, 否则走 hurtAndBreak);
        // 水桶与岩浆桶**刻意不消耗** —— 不 shrink、不给空桶。
        if (held.is(Items.FLINT_AND_STEEL))
            consumeHeld(player, held, hand, false);

        // 提示里报的是**这一次真正转换掉的数量**(台面 + 并入的原料堆)
        player.displayClientMessage(
            Component.translatable("msg." + BetterWrenchMod.MODID + ".assemble.fan_done", batch.getCount()), true);
        return true;
    }

    /**
     * 手持物 → **候选**鼓风处理类型列表(按序尝试, 取第一个既 {@code canProcess} 又有配方的);
     * 手持物不是这三种之一则返回空列表(调用方直接跳过本路径)。
     *
     * <p>打火石要看**置物台下方**的方块: 灵魂沙 / 灵魂土(原版 {@code BlockTags.SOUL_FIRE_BASE_BLOCKS},
     * 灵魂火就架在这两种方块上)或灵魂火本身 ⇒ **先试缠魂、再回退烟熏** —— 灵魂底座上烤食物时
     * 缠魂本来就没配方, 回退烟熏才不会"占着路径不干活"(2026-09-20 用户要求)。</p>
     */
    private static List<FanProcessingType> fanTypesFor(Level level, BlockPos pos, ItemStack held) {
        if (held.is(Items.WATER_BUCKET))
            return List.of(AllFanProcessingTypes.SPLASHING);
        if (held.is(Items.LAVA_BUCKET))
            return List.of(AllFanProcessingTypes.BLASTING);
        if (!held.is(Items.FLINT_AND_STEEL))
            return List.of();
        return isSoulBase(level, pos.below())
            ? List.of(AllFanProcessingTypes.HAUNTING, AllFanProcessingTypes.SMOKING)
            : List.of(AllFanProcessingTypes.SMOKING);
    }

    /**
     * 该方块是否属于"灵魂火底座": 灵魂沙 / 灵魂土(vanilla tag), 外加灵魂火本身。
     *
     * <p>⚠️ 复审 B-17: 这是**全模组唯一**一份判据 —— `DepotSoulFlames`(锁定置物台的灵魂火焰粒子)也调用本方法,
     * 不再各留一份拷贝(否则 Create 改了 tag 语义时会有一边不同步)。</p>
     */
    static boolean isSoulBase(Level level, BlockPos below) {
        BlockState state = level.getBlockState(below);
        return state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS) || state.is(Blocks.SOUL_FIRE);
    }

    /**
     * 类鼓风成功时的**一次性反馈**: 原版音效 + 与 Create 鼓风机**同款**的粒子。
     *
     * <h2>音效(用户指定: 就用原版这三个)</h2>
     * <ul>
     *   <li>水桶 ⇒ {@link SoundEvents#BUCKET_EMPTY}(和原版倒水一样);</li>
     *   <li>岩浆桶 ⇒ {@link SoundEvents#BUCKET_EMPTY_LAVA};</li>
     *   <li>打火石 ⇒ {@link SoundEvents#FLINTANDSTEEL_USE}(音高照原版 {@code FlintAndSteelItem} 那样随机抖动)。</li>
     * </ul>
     * 本路径**不再**播拾取音({@link #playPickup}) —— 与上面的音效叠在一起会很浑。
     *
     * <h2>粒子(照抄 Create 的 `FanProcessingType#spawnProcessingParticles`)</h2>
     * 逐个类型核对过上游 `AllFanProcessingTypes` 里四个实现, 只保留**粒子种类与颜色**(含 y 偏移):
     * <ul>
     *   <li>洗涤 `SplashingType`(417-425 行): {@code DustParticleOptions(0x0055FF, 1)} + {@code SPIT}, 台面上方 0.5;</li>
     *   <li>冶炼 `BlastingType`(170-174 行): {@code LARGE_SMOKE}, 上方 0.25;</li>
     *   <li>烟熏 `SmokingType`(356-360 行): {@code POOF}, 上方 0.25;</li>
     *   <li>缠魂 `HauntingType`(236-246 行): {@code SOUL_FIRE_FLAME}(上方 0.45) + {@code SMOKE}
     *       (上游是 {@code random.nextInt(2) == 0} 的 1/2 概率, 这里按"一半量级"取定量 4 个)。</li>
     * </ul>
     * <p><b>两处刻意不同(其余照搬):</b></p>
     * <ol>
     *   <li>上游是**每 tick**调一次、且自带 {@code random.nextInt(8) != 0 → return}(1/8 概率)的门槛,
     *       而且用的是 {@code level.addParticle}(**只在客户端有效**, 服务端是空实现)。
     *       我们这边是"一次右击 = 一次转换", 所以改成**服务端 {@code sendParticles} 喷一小撮**:
     *       附近所有玩家都能看见、也无需新增网络包。</li>
     *   <li>上游靠 {@code (0, 1/16, 0)} 这类微小初速; 这个 API 只能给"随机速度", 给不了固定向上初速 ——
     *       但这几种粒子本身就有上浮/扩散的物理(LARGE_SMOKE/POOF/SOUL_FIRE_FLAME 上浮, SPIT 受重力),
     *       观感与鼓风机一致, 故不再为它绕道自定义网络包。</li>
     * </ol>
     */
    private static void playFanFeedback(Level level, BlockPos pos, ItemStack held, FanProcessingType type) {
        // ---- 音效 ----
        if (held.is(Items.WATER_BUCKET))
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        else if (held.is(Items.LAVA_BUCKET))
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1f, 1f);
        else if (held.is(Items.FLINT_AND_STEEL))
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f,
                level.random.nextFloat() * 0.4f + 0.8f);

        // ---- 粒子(远端半径内所有玩家都看得见) ----
        if (!(level instanceof ServerLevel serverLevel))
            return;
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;   // 置物台台面正上方, 与产出弹出的锚点一致
        double z = pos.getZ() + 0.5;
        if (type == AllFanProcessingTypes.SPLASHING) {
            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0f, 0x55 / 255f, 1f), 1f),
                x, y + 0.5, z, 8, 0.25, 0.05, 0.25, 0.05);
            serverLevel.sendParticles(ParticleTypes.SPIT, x, y + 0.5, z, 8, 0.25, 0.05, 0.25, 0.05);
        } else if (type == AllFanProcessingTypes.BLASTING) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.25, z, 8, 0.2, 0.05, 0.2, 0.02);
        } else if (type == AllFanProcessingTypes.SMOKING) {
            serverLevel.sendParticles(ParticleTypes.POOF, x, y + 0.25, z, 8, 0.2, 0.05, 0.2, 0.02);
        } else if (type == AllFanProcessingTypes.HAUNTING) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.45, z, 8, 0.15, 0.05, 0.15, 0.01);
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y + 0.25, z, 4, 0.15, 0.05, 0.15, 0.01);
        }
    }

    private static long countRaw(Level level, BlockPos pos) {
        // 只要一个上界; 真正的取出由 DepotPiles.take 完成
        return DepotPiles.hasAny(level, pos) ? 64L : 0L;
    }

    // ---------------------------------------------------------------- 台面 / 料堆

    /** 台面物品多于 1 个时, 把多余的挪到原料堆; 台面只留 1 个(正在加工的那个)。 */
    private static void splitExtras(Level level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack current = depot.getHeldItem();
        if (current.getCount() <= 1)
            return;
        ItemStack extras = current.copyWithCount(current.getCount() - 1);
        setDepot(depot, current.copyWithCount(1));
        DepotPiles.deposit(level, pos, extras);
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
     * 生成一个「加工产出」的**普通掉落物** —— 不打持久化标记、不 {@code setUnlimitedLifetime()},
     * 因此可正常拾取、也会像普通掉落物一样正常消失。
     *
     * <p>两种形态:</p>
     * <ul>
     *   <li>{@code launched == true}: 台面正上方的出口, 带向上的初速
     *       (保留"从台面上弹出来"的观感, 见 {@link #ejectHeldAndRefill});</li>
     *   <li>{@code launched == false}: 在给定锚点原地落下、无初速(注液批量产出用, 锚点取
     *       {@link #productDropPos})。</li>
     * </ul>
     */
    private static void dropProduct(Level level, Vec3 anchor, ItemStack stack, boolean launched) {
        if (level.isClientSide || stack.isEmpty())
            return;
        ItemEntity drop = new ItemEntity(level, anchor.x, anchor.y, anchor.z, stack);
        if (launched)
            drop.setDeltaMovement(
                (level.random.nextDouble() - 0.5) * 0.12,
                0.22,
                (level.random.nextDouble() - 0.5) * 0.12);
        else
            drop.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(drop);
    }

    /**
     * 产出收集点: 置物台**东南侧**正上方(原「成品堆」所在的角落), 与西北角的原料堆分开,
     * 这样批量注液的产出不会和原料堆混在一处。
     */
    private static Vec3 productDropPos(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5 + PRODUCT_DROP_OFFSET, pos.getY() + 1.0,
            pos.getZ() + 0.5 + PRODUCT_DROP_OFFSET);
    }

    /**
     * 停留时间到: 把台面上那件成品**弹出**, 然后自动续上原料堆的下一个。
     * 只由 {@link DepotProductEjector} 调用。
     */
    static void ejectHeldAndRefill(ServerLevel level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty())
            return;
        // 弹出物是**普通掉落物**了(带向上初速, 保留"从台面弹出来"的观感); 它落地后不会被置物台
        // 吸走, 因为下一行 consumeAndRefill 已经把台面占上了(占用时置物台拒收掉落物)。
        dropProduct(level, new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
            held.copy(), true);
        consumeAndRefill(level, pos, depot);       // 台面清空 + 立刻续下一个原料
    }

    /**
     * 解锁置物台时: 把**台面上正在加工的那一个**和**原料堆**全部**返还到玩家背包**。
     *
     * <p>用户要求(2026-09-17):「在解锁置物台后, 把所有置物台上面的东西都返还到玩家背包(包括掉落物堆)」。</p>
     *
     * <p>⚠️ 「成品堆」已经不在了: 加工产出落在地上就是**普通掉落物**, 属于世界而不是这个置物台,
     * 所以本方法**不会**去捡已经弹出的成品 —— 它们留在原地等玩家自己拾取(也会正常消失)。</p>
     *
     * <p>装不下的部分由原版 {@code Inventory.placeItemBackInInventory} 负责掉在玩家脚下, **不会凭空消失**。</p>
     */
    public static void returnHeldAndPiles(ServerLevel level, BlockPos pos, DepotBlockEntity depot, ServerPlayer player) {
        // 解锁/停用: 该坐标上还在排队的「停留后弹出」条目作废, 否则到点会弹出台面上后来放的东西
        DepotProductEjector.cancelAt(level, pos);
        // ① 台面上的那一个
        ItemStack held = depot.getHeldItem();
        if (!held.isEmpty()) {
            setDepot(depot, ItemStack.EMPTY);
            give(player, held);
        }
        // ② 原料堆
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
        ItemStack next = DepotPiles.take(level, pos, 1);
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
     * 的 `isOccupied()`) ⇒ 成品会稳稳停在台面上而不被吸走。
     * 只有原料堆也空了(一个批次加工完)才会被收进去, 那时正好也该收工了。</p>
     */
    private static void consumeAndRefill(Level level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack next = DepotPiles.take(level, pos, 1);
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
