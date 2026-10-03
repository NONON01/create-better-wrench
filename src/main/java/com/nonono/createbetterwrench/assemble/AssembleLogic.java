package com.nonono.createbetterwrench.assemble;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.nonono.createbetterwrench.config.WrenchConfig;
import com.nonono.createbetterwrench.mode.ProcessKind;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.joml.Vector3f;

/**
 * 「工作」(内部 id 仍为 {@code assemble})模式的服务端核心: 在<b>已锁定</b>的置物台上,
 * 用手代替机器, 依次尝试六种机制(全部走 Create/原版自己的配方体系, 不硬编码具体配方):
 *
 * <ol>
 *   <li><b>序列装配</b> —— {@code create:sequenced_assembly}, 等价于发射器(Deployer)推进装配。</li>
 *   <li><b>机械手式施加</b> —— {@code create:deploying} 与 {@code create:item_application}。
 *       查找顺序与优先级完全照搬 {@code DeployerBlockEntity#getRecipe}。</li>
 *   <li><b>原木去皮</b> —— 原版 {@link AxeItem} 机制(Create 只在 JEI 里展示这条隐式配方)。</li>
 *   <li><b>锻板</b>(本次新增) —— 手持<b>原版重锤</b>({@code minecraft:mace})右击: 把台上的金属锭按 Create 的
 *       压板配方({@code create:pressing})锻成金属板。与其它路径不同, 它<b>一次只做一件</b>, 并且每次锻击后
 *       给锤子加 5 tick 冷却。见 {@link Forging#tryOnDepot}。</li>
 *   <li><b>注液</b> —— {@code create:filling}, 等价于注液器(Spout)。</li>
 *   <li><b>批量鼓风处理</b>(2026-09-20 新增) —— 模仿 Create 鼓风机: 水桶走洗涤({@code create:splashing})、
 *       岩浆桶走冶炼(熔炉/高炉)、打火石走烟熏; 若置物台<b>下方</b>是灵魂沙 / 灵魂土 / 灵魂火则走缠魂
 *       ({@code create:haunting}), 缠魂没有配方时<b>回退烟熏</b>。一次把<b>一整摞</b>物品完全转换
 *       (台面那一摞 + 原料堆里的同类; 上限为物品自身的最大堆叠数, 2026-09-22 之前由配置项
 *       {@code assemble.fan_batch_limit} 提供, 该配置项已删除), 产出<b>全部弹出</b>。
 *       见 {@link #tryFanProcessing}。</li>
 * </ol>
 *
 * <h2>两个位置</h2>
 * 一个置物台只能渲染一个物品堆, 所以台面<b>只放正在加工的那一个</b>, 原料则由
 * {@link DepotPiles} 用掉落物实体摆在置物台的<b>西北角</b>(带正常重力, 会自己落在台面上):
 * <pre>
 *   原料堆(RAW, 西北角, 锁定期间不可拿)   ←  台面中央(正在加工)
 * </pre>
 *
 * <p><b>「成品堆」已不存在</b>: 加工产出(含注液批量产出)一律作为<b>普通掉落物</b>
 * 直接落在世界上 —— 不打任何持久化标记、不 {@code setUnlimitedLifetime()}, 因此会像普通掉落物
 * 一样被拾取、也会正常消失。解锁置物台时只返还台面上那一个与原料堆。</p>
 *
 * <p><b>自动续料</b>: 每加工完一件, {@link #consumeAndRefill} 会在<b>同一个 tick 内</b>把原料堆的下一个
 * 顶上台面, 所以台面在原料堆还有货时不会空着, 玩家加工完一件就能直接接着下一件
 * (无需先右键放料再右键加工)。装配<b>失败</b>的产物直接弹成普通掉落物。</p>
 */
public final class AssembleLogic {

    private static final org.slf4j.Logger LOGGER = BetterWrenchMod.LOGGER;

    /**
     * 注液批量产出的落点相对置物台中心的水平偏移(东南侧)。
     * 产出落点与原料堆(西北角)分处两侧, 避免产出与原料混在同一区域。
     *
     * <p>注意: 复审 B-17 —— 本常量直接引用 {@link DepotPiles#CORNER_OFFSET}, 不另写一个 0.27,
     * 因为这两处<b>必须同值</b>(一西一东的对角对称), 分开写会导致只改一处而漏改另一处。</p>
     */
    private static final double PRODUCT_DROP_OFFSET = DepotPiles.CORNER_OFFSET;

    /** 产出落点: 摆在玩家<b>背后</b>一侧(与两个料堆相对), 基准朝向同样取锁定时的朝向。 */
    private static double[] productOffset(Level level, BlockPos pos) {
        net.minecraft.core.Direction facing = DepotPiles.facingAt(level, pos);
        return new double[] { -facing.getStepX() * PRODUCT_DROP_OFFSET, -facing.getStepZ() * PRODUCT_DROP_OFFSET };
    }

    private AssembleLogic() {
    }

    public static boolean tryAssemble(Level level, BlockPos pos, DepotBlockEntity depot,
                                      Player player, ItemStack held, InteractionHand hand) {
        if (held.isEmpty() || held.is(BetterWrenchMod.BETTER_WRENCH))
            return false; // 空手/扳手不参与工作模式(扳手用于锁定/解锁)

        // ⓪ 功能开关(2026-09-25): 「加工」总开关关闭后, 本方法不做任何加工, 只发送提示并吃掉这次交互
        //    (返回 true 表示已消费, AssembleInteractionHandler 因此不会把手上那摞放上台面)。
        if (!WrenchConfig.processConfigEnabled()) {
            notifyFeatureDisabled(player);
            return true;
        }

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
        if (Forging.tryOnDepot(level, pos, depot, player, held, hand, current))
            return true;
        if (trySpoutFilling(level, pos, depot, player, held, hand, current))
            return true;
        // 注意: 必须排在 trySpoutFilling 之后 —— 岩浆桶注液成烈焰蛋糕那条路径已实机验证,
        // 排在前面会抢走水桶/岩浆桶的注液用途(鼓风处理会先判 canProcess)。
        if (tryFanProcessing(level, pos, depot, player, held, hand, current))
            return true;
        return false;
    }

    // ---------------------------------------------------------------- 功能开关(2026-09-25)

    /** actionbar: 整个「加工」功能被配置关掉了。 */
    private static void notifyFeatureDisabled(Player player) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
            "msg." + BetterWrenchMod.MODID + ".feature_disabled"), true);
    }

    /**
     * 该子功能是否被配置关闭。关闭时就地给 actionbar 发送
     * {@code msg.create_better_wrench.subfeature_disabled} 并返回 {@code true}
     * (调用方返回 true 表示消费该次交互, 不消耗任何物品)。
     */
    static boolean blockedSubKind(Player player, ProcessKind kind) {
        if (WrenchConfig.processKindEnabled(kind))
            return false;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
            "msg." + BetterWrenchMod.MODID + ".subfeature_disabled"), true);
        return true;
    }

    /** 把 Create 的鼓风类型映射到本模组定义的七个子功能之一; 映射不到时返回 null, 该类型只受总开关约束。 */
    private static ProcessKind kindOf(FanProcessingType type) {
        if (type == AllFanProcessingTypes.SPLASHING)
            return ProcessKind.SPLASH;
        if (type == AllFanProcessingTypes.BLASTING)
            return ProcessKind.BLASTING;
        if (type == AllFanProcessingTypes.SMOKING)
            return ProcessKind.SMOKING;
        if (type == AllFanProcessingTypes.HAUNTING)
            return ProcessKind.HAUNTING;
        return null;
    }

    // ---------------------------------------------------------------- 1. 序列装配

    private static boolean trySequencedAssembly(Level level, BlockPos pos, DepotBlockEntity depot,
                                                Player player, ItemStack held, InteractionHand hand,
                                                ItemStack current) {
        // 序列的每一步都是"独立配方 + 序列上下文", 因此三种步骤类型都要用序列感知的查找问一遍。
        // 顺序: deploying -> 注液 -> 冲压; 只有手持物满足该步要求时才由本方法消费这次交互。
        Optional<RecipeHolder<DeployerApplicationRecipe>> deploying = SequencedAssemblyRecipe.getRecipe(
            level, current, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class);
        if (deploying.isPresent() && deploying.get().value().getRequiredHeldItem().test(held)) {
            DeployerApplicationRecipe recipe = deploying.get().value();
            if (blockedSubKind(player, ProcessKind.ASSEMBLY))
                return true;
            ItemStack working = current.copyWithCount(1);
            splitExtras(level, pos, depot);
            List<ItemStack> results = RecipeApplier.applyRecipeOn(level, working, recipe, true);
            consumeHeld(player, held, hand, recipe.shouldKeepHeldItem());
            return finishSequenceStep(level, pos, depot, player, results);
        }

        Optional<RecipeHolder<FillingRecipe>> filling = SequencedAssemblyRecipe.getRecipe(
            level, current, AllRecipeTypes.FILLING.getType(), FillingRecipe.class);
        if (filling.isPresent()
            && tryApplySequenceFilling(level, pos, depot, player, held, hand, current, filling.get().value()))
            return true;

        Optional<RecipeHolder<PressingRecipe>> pressing = SequencedAssemblyRecipe.getRecipe(
            level, current, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class);
        if (pressing.isPresent()
            && tryApplySequencePressing(level, pos, depot, player, held, hand, current, pressing.get().value()))
            return true;

        return false;
    }

    /**
     * 序列装配的<b>注液步</b>({@code create:filling} 步骤): 手持流体容器提供该步所需流体。
     *
     * <p><b>按桶内 ml 数批量处理</b>(2026-10-03 维护者确认): 先用容器可排出的流体量除以"每件所需量",
     * 得到这一次最多能注几件; 再逐件施加该步配方, 把<b>全部</b>中间产物先送入半成品堆, 最后按
     * "半成品堆优先"自动补 1 件到台面, 让下一步可以直接继续。</p>
     *
     * <p>材料不足时按既有约定从原料堆续料(取来的部分失败时原样退回, 不静默丢料)。
     * 容器在成功后会整份排空并把剩余物(空桶/空瓶)交还玩家 —— 与「注液」路径的语义一致。</p>
     */
    private static boolean tryApplySequenceFilling(Level level, BlockPos pos, DepotBlockEntity depot,
                                                   Player player, ItemStack held, InteractionHand hand,
                                                   ItemStack current, FillingRecipe recipe) {
        if (!GenericItemEmptying.canItemBeEmptied(level, held))
            return false;
        FluidStack available = GenericItemEmptying.emptyItem(level, held.copy(), true).getFirst();
        SizedFluidIngredient required = recipe.getRequiredFluid();
        if (available.isEmpty() || !required.ingredient().test(available))
            return false;
        int perItem = Math.max(1, required.amount());
        int units = available.getAmount() / perItem;      // 这一份流体够注几件
        if (units <= 0)
            return false;
        if (blockedSubKind(player, ProcessKind.ASSEMBLY))
            return true;

        // 输入 = 台面现有的 + 从原料堆续上来的, 最多凑到 units 件
        int onDepot = current.getCount();
        int wanted = Math.min(units, onDepot + (int) Math.min(Integer.MAX_VALUE, countRaw(level, pos)));
        ItemStack combined = current.copy();
        int extraMerged = 0;
        if (wanted > onDepot) {
            ItemStack extra = DepotPiles.take(level, pos, wanted - onDepot);
            if (!extra.isEmpty()) {
                if (ItemStack.isSameItemSameComponents(combined, extra)) {
                    extraMerged = extra.getCount();
                    combined.grow(extraMerged);
                } else {
                    DepotPiles.deposit(level, pos, extra);
                }
            }
        }
        int toFill = Math.min(units, combined.getCount());
        if (toFill <= 0)
            return false;

        splitExtras(level, pos, depot);   // 台面多出来的部分进原料堆(combined 已包含它们)
        List<ItemStack> results = new ArrayList<>();
        int made = 0;
        for (int i = 0; i < toFill; i++) {
            List<ItemStack> one = RecipeApplier.applyRecipeOn(level, combined.copyWithCount(1), recipe, true);
            if (one.isEmpty() || one.get(0).isEmpty())
                break;
            for (ItemStack result : one)
                if (!result.isEmpty())
                    results.add(result.copy());
            combined.shrink(1);
            made++;
        }
        if (made <= 0) {
            // 一份也未注成: 把从原料堆取走的输入原样退回, 避免静默丢料
            if (extraMerged > 0)
                DepotPiles.deposit(level, pos, combined.copyWithCount(extraMerged));
            return false;
        }

        // 真正排空容器; 剩余物(空桶/空瓶等)交还玩家
        Pair<FluidStack, ItemStack> drained = GenericItemEmptying.emptyItem(level, held.copy(), false);
        if (!player.isCreative()) {
            ItemStack remainder = drained.getSecond();
            held.shrink(1);
            if (held.isEmpty())
                player.setItemInHand(hand, remainder);
            else if (!remainder.isEmpty() && !player.getInventory().add(remainder))
                player.drop(remainder, false);
        }
        // 台面上还没处理的材料留在台面(已入半成品堆的部分不在其中)
        if (!combined.isEmpty())
            setDepot(depot, combined.copy());
        return finishSequenceStep(level, pos, depot, player, results);
    }

    /**
     * 序列装配的<b>冲压步</b>({@code create:pressing} 步骤): 手持锤类物品即可, 与锻造共用同一份判据与冷却。
     */
    private static boolean tryApplySequencePressing(Level level, BlockPos pos, DepotBlockEntity depot,
                                                    Player player, ItemStack held, InteractionHand hand,
                                                    ItemStack current, PressingRecipe recipe) {
        if (!Forging.isHammer(held) || player.getCooldowns().isOnCooldown(held.getItem()))
            return false;
        if (blockedSubKind(player, ProcessKind.ASSEMBLY))
            return true;

        ItemStack working = current.copyWithCount(1);
        splitExtras(level, pos, depot);
        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, working, recipe, true);
        consumeHeld(player, held, hand, false);
        int cooldown = Forging.cooldownTicks();
        if (cooldown > 0)
            player.getCooldowns().addCooldown(held.getItem(), cooldown);
        return finishSequenceStep(level, pos, depot, player, results);
    }

    /**
     * 序列某一步施加完成后的<b>共用收尾</b>: 多余的产出弹出, 主产出判断"还能不能继续" ——
     * 能继续就留在台面等下一步, 否则按「停留后弹出」交付成品。
     *
     * <p>这一步与<b>步骤类型无关</b>, 因此 deploying / 注液 / 冲压三种步骤共用, 避免各写一份而出现差异。</p>
     */
    private static boolean finishSequenceStep(Level level, BlockPos pos, DepotBlockEntity depot,
                                              Player player, List<ItemStack> results) {
        ItemStack out = results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
        if (out.isEmpty()) {
            consumeAndRefill(level, pos, depot);
            return true;
        }
        if (canContinueSequence(level, out)) {
            // 中间产物进半成品堆, 并且不再立刻取回台面(2026-10-03 按设计约定修正):
            //   早期实现在放入后又立即取回台面, 半成品堆因此始终为空。
            //   现在中间产物留在堆里, 由下一次交互按"半成品堆优先"续料时取回台面。
            DepotPiles.depositSemi(level, pos, out);
            depositExtrasToSemi(level, pos, results);
            // 规格(2026-10-03 维护者确认): 先把这个步骤产出的<b>全部</b>中间产物归入半成品堆,
            //   再按"半成品堆优先"自动补 1 件到台面, 让下一步可以直接继续。
            ItemStack topped = DepotPiles.takeFrom(level, pos, 1, DepotPiles.SEMI);
            if (!topped.isEmpty())
                setDepot(depot, topped);
            LOGGER.info("[CBW/加工] 序列步产出 {} 件 -> 半成品堆, 自动补台面 1 件={}, 半成品堆剩余={}",
                results.size(), topped.isEmpty() ? "无" : topped.getHoverName().getString(),
                DepotPiles.hasSemi(level, pos));
            playPickup(level, pos);
            return true;
        }
        // 序列已结束: 主产出按「停留后弹出」交付, 其余产出仍属于中间产物, 入半成品堆
        depositExtrasToSemi(level, pos, results);
        holdThenEject(level, pos, depot, out, player);
        playPickup(level, pos);
        return true;
    }

    /**
     * 序列装配与注液步骤的<b>额外产出</b>(第 2 件起)送进半成品堆。
     *
     * <p>它们与主产物不同: 主产物决定序列能否继续, 额外产出只是这一步的副产物, 属于半成品堆的范畴
     * (设计约定, 2026-10-03), 因此不再像以前那样直接弹到地上。</p>
     */
    private static void depositExtrasToSemi(Level level, BlockPos pos, List<ItemStack> results) {
        for (int i = 1; i < results.size(); i++)
            if (!results.get(i).isEmpty())
                DepotPiles.depositSemi(level, pos, results.get(i));
    }

    /**
     * 该中间产物后面还有没有下一步 —— <b>三种步骤类型都要问一遍</b>。
     *
     * <p>2026-10-03 修复: 旧实现只问 {@code DEPLOYING}, 于是 Create 自带的
     * {@code track}(deploying, deploying, pressing)与 {@code sturdy_sheet}(filling, pressing, pressing)
     * 会在非 deploying 的那一步被误判成"序列已结束", 直接把中间产物当成品弹出。</p>
     */
    private static boolean canContinueSequence(Level level, ItemStack out) {
        return SequencedAssemblyRecipe
            .getRecipe(level, out, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class)
            .isPresent()
            || SequencedAssemblyRecipe
                .getRecipe(level, out, AllRecipeTypes.FILLING.getType(), FillingRecipe.class)
                .isPresent()
            || SequencedAssemblyRecipe
                .getRecipe(level, out, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class)
                .isPresent();
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
        // 子功能开关: 机械手式施加归在「装配」下(本模组定义的七个子功能里没有单独一类)
        if (blockedSubKind(player, ProcessKind.ASSEMBLY))
            return true;

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

    // ---------------------------------------------------------------- 4. 锻板

    // ---------------------------------------------------------------- 5. 注液

    /**
     * 等价于注液器, 但<b>按流体实量批量注</b>:
     * 一份配方可能只吃 25mB(例如发光石), 而玩家手里一个桶是 1000mB,
     * 所以一次操作会把 {@code 1000 / 单份用量} 个物品一起注满(不超过实际可用的输入数量)。
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
        // 子功能开关: 「注液」
        if (blockedSubKind(player, ProcessKind.FILLING))
            return true;

        // 输入 = 台面现有的 + 从原料堆续上来的, 最多凑到 units 个
        int onDepot = current.getCount();
        int wanted = Math.min(units, onDepot + (int) Math.min(Integer.MAX_VALUE, countRaw(level, pos)));
        ItemStack combined = current.copy();
        int extraMerged = 0;   // 实际并入 combined 的、从原料堆取来的数量(失败时要原样退回)
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

        // 注液配方: 用于取到<b>全部</b>产出。
        //   FillingBySpout#fillItem 只返回主产出(单个 ItemStack), 多于一件的产出会被丢掉 ——
        //   这正是"注液的多产出没了"的原因(2026-10-03)。改为用 RecipeApplier 施加并收集全部产出。
        // 注意: Create 的 AllRecipeTypes.FILLING 泛型是基类型, 直接取会被推断成 RecipeHolder<Recipe<...>>,
        //   因此这里遍历并按 instanceof 收窄到 FillingRecipe。
        FillingRecipe fillingRecipe = null;
        for (RecipeHolder<?> holder : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.FILLING.getType())) {
            if (holder.value() instanceof FillingRecipe candidate
                && !candidate.getIngredients().isEmpty()
                && candidate.getIngredients().get(0).test(probe)) {
                fillingRecipe = candidate;
                break;
            }
        }

        int made = 0;
        List<ItemStack> products = new ArrayList<>();
        for (int i = 0; i < toFill && !combined.isEmpty(); i++) {
            List<ItemStack> results;
            if (fillingRecipe != null)
                results = RecipeApplier.applyRecipeOn(level, combined.copyWithCount(1), fillingRecipe, true);
            else
                results = List.of(FillingBySpout.fillItem(level, perItem, combined.copyWithCount(1), available.copy()));
            if (results.isEmpty() || results.get(0).isEmpty())
                break;
            for (ItemStack result : results)
                if (!result.isEmpty())
                    products.add(result.copy());
            // 审计 B-6: 显式扣减这一轮的输入, 不再靠 combined.getCount() - made 的算术对消
            combined.shrink(1);
            made++;
        }
        if (made <= 0) {
            // 注意: 若一份也未注成, 必须把从原料堆取走的输入原样退回。
            //    这些物品已经离开料堆实体、只存在于 combined 里, 直接 return 会静默丢失。
            //    (台面那部分无需退回 —— 其仍位于置物台上, 本流程未修改该位置的状态。)
            if (extraMerged > 0)
                DepotPiles.deposit(level, pos, combined.copyWithCount(extraMerged));
            return false;
        }

        // 产出: 批量注液一次可能做出多件, 台面只有一个位置放不下, 因此直接进玩家背包(不经过停留档位)
        for (ItemStack product : products)
            giveToPlayer(player, product);

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

    // ---------------------------------------------------------------- 6. 鼓风处理(批量)

    /**
     * 第 5 条路径: 用水桶 / 岩浆桶 / 打火石模拟 Create 鼓风机的一次性处理,
     * 但对<b>一整摞</b>物品一次性完成 —— 洗涤({@code create:splashing})、冶炼(原版熔炉/高炉)、
     * 烟熏(原版烟熏炉)、缠魂({@code create:haunting})。
     *
     * <p>三种手持物的含义(候选类型按序尝试):</p>
     * <ul>
     *   <li>{@link Items#WATER_BUCKET}: 洗涤({@code SPLASHING});</li>
     *   <li>{@link Items#LAVA_BUCKET}: 冶炼({@code BLASTING});</li>
     *   <li>{@link Items#FLINT_AND_STEEL}: 置物台<b>下方</b>是灵魂沙 / 灵魂土 / 灵魂火时<b>先试缠魂</b>
     *       ({@code HAUNTING}), 缠魂没有配方(例如台面是食物)再<b>回退烟熏</b>({@code SMOKING});
     *       下方不是灵魂底座时只试烟熏。</li>
     * </ul>
     *
     * <p><b>一次转换多少:</b> 台面那一摞 + 原料堆里的<b>同类</b>物品, 合计上限 = <b>该物品的最大堆叠数</b>
     * (置物台本身的理论上限, 原版即 64), 即一次右击转换一整摞。原料堆取来的部分只在与台面物品
     * <b>物品与组件完全一致</b>时才并入, 且<b>加工失败时原样退回原料堆</b>
     * (与 {@code trySpoutFilling} 的做法一致, 不会静默吞料)。</p>
     *
     * <p><b>为什么排在 filling 之后:</b> 注液(岩浆桶注成烈焰蛋糕等)是已实机验证过的路径,
     * 必须先给它机会; 本路径只作为注液未命中时的兜底。详见 {@link #tryAssemble} 的调用点注释。</p>
     *
     * <p><b>与 Create 的语义差异:</b> 上游 {@code FanProcessingType#process} 返回
     * <b>null</b> 或 <b>空列表</b> 时, Create 的 {@code FanProcessing.applyProcessing} 会
     * {@code entity.discard()}, 即<b>销毁物品</b>(例如被岩浆烧掉的非防火物品)。
     * 本模组刻意反过来: 一律按不适用处理, <b>保持台面物品原样、不销毁玩家物品</b>。</p>
     *
     * <p><b>消耗:</b> 水桶 / 岩浆桶<b>完全不消耗</b>(也不给空桶); 打火石只走
     * {@link #consumeHeld} 的耐久分支 —— 无论一次转换多少个, 都只扣 <b>1 点耐久</b>。</p>
     *
     * <p><b>产出:</b> 整批交给 {@code process} 一次即代表整批完全转换
     * ({@code RecipeApplier.applyRecipeOn} 内部按 {@code getCount()} 逐份掷结果, 并把同类产出
     * 合并成尽量满的堆叠)。弹出策略按<b>本次结果的种类数</b>二分(设计约定, 2026-09-22):
     * <b>多种产品时全部直接弹出</b>(台面只有一个位置, 多产品留不住);
     * <b>单一产品时稍作停留</b> —— 摆上台面, 按 Ctrl+滚轮选择的<b>停留档位</b>(0/2/4/8 tick)到点再弹,
     * 并在弹出那一刻从原料堆续下一份原料(与其它四条路径完全一致; 不停留档即等于立刻弹出)。</p>
     *
     * <p><b>反馈(2026-09-20 追加):</b> 成功时播<b>原版音效</b> —— 水桶为 {@code BUCKET_EMPTY}(倒水)、
     * 岩浆桶为 {@code BUCKET_EMPTY_LAVA}、打火石为 {@code FLINTANDSTEEL_USE}; 同时喷洒与 Create 鼓风机
     * <b>同款</b>的粒子(洗涤 = 蓝色尘 + {@code SPIT}, 冶炼 = {@code LARGE_SMOKE}, 烟熏 = {@code POOF},
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

        // ① 先只拿台面那一个探配方(canProcess 与数量无关) —— 这样没有配方时不会白动原料堆。
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
        // 子功能开关: 洗涤/冶炼/烤制/缠魂 —— 具体是哪一种由上面挑出来的 FanProcessingType 决定
        ProcessKind kind = kindOf(type);
        if (kind != null && blockedSubKind(player, kind))
            return true;

        // ② 并入原料堆里的同类物品, 合计凑到置物台本身的理论上限(= 该物品的最大堆叠数, 原版即 64)。
        //    原料堆里的异类型物品原样放回(与 trySpoutFilling 同样的防丢料处理)。
        //    2026-09-22: 这里原来读配置项 assemble.fan_batch_limit, 因置物台本身上限即为 64, 无需做成配置,
        //       该配置项已删除, 现在直接取物品自己的最大堆叠数。
        int limit = Math.max(1, current.getMaxStackSize());
        ItemStack batch = current.copy();
        // 台面那一摞若超过上限(例如配置把上限调得小于台面数量): 只有 batch 这部分会被转换, 多出来的部分
        // 先留在这个局部变量里、不碰世界 —— 成功后才退回原料堆; 失败时台面原封不动。
        // 注意: 不能在这里就 deposit —— 那样先退料、后失败会让台面那一摞重复一份(退走的 + 台面上的)。
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

        // ③ process 可能返回 null(无配方)或空列表(不适用, 在 Create 里表示销毁) —— 两者都按不适用处理。
        //    还要挡住列表非空、但元素全是空栈的情形: 上游 {@code ItemHelper.multipliedOutput} 在产物为空时
        //    会无条件 add 一个 count=0 的栈(ItemHelper.java:49-59), 而 BlastingType.process 只判配方是否存在。
        //    只判 out.isEmpty() 的话, 这种批次会走成功分支被静默丢掉(不投物、无提示), 因此要求至少一个非空产出。
        List<ItemStack> out = type.process(batch.copy(), level);
        boolean anyOutput = false;
        if (out != null)
            for (ItemStack stack : out)
                if (!stack.isEmpty()) {
                    anyOutput = true;
                    break;
                }
        if (!anyOutput) {
            // 注意: 失败时必须把从原料堆取来的那部分原样退回 —— 那些物品已经离开料堆实体、
            //    只存在于 batch 里, 直接 return 会静默丢失。(台面那部分没动过, 无需处理。)
            if (fromPile > 0)
                DepotPiles.deposit(level, pos, current.copyWithCount(fromPile));
            player.displayClientMessage(
                Component.translatable("msg." + BetterWrenchMod.MODID + ".assemble.fan_none"), true);
            return true; // 这次手势已被本模组消费: 保持台面原样
        }

        // ④ 产出弹出策略(设计约定, 2026-09-22):
        //    多种产品时全部直接弹出(台面只有一个位置, 多产品既留不住、也会互相卡位; 弹出后立刻续料);
        //    单一产品时稍作停留: 摆上台面, 按加工模式 Ctrl+滚轮的停留档位(0/2/4/8 tick)到点再弹,
        //    续料由弹出那一刻的 ejectHeldAndRefill 完成 —— 与其它四条路径完全一致。
        //    已知限制与修订原因: 早期按直接弹出实现时绕过了 holdThenEject, 导致滚轮四档失效;
        //    但多产品又不能强行占台面, 因此按上面的规则分两种策略。判据用本次实际结果的种类数
        //    (非空栈数量): 概率性副产物这一批没掷出来时就按单一产品处理, 即稍作停留, 无副作用。
        //
        // 注意: 先处理台面超出上限的那部分 —— 两条策略都会覆盖台面那一摞, 不在这里退回原料堆就是静默丢失。
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
                    giveToPlayer(player, stack.copy());
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
        // 水桶与岩浆桶刻意不消耗 —— 不 shrink、不给空桶。
        if (held.is(Items.FLINT_AND_STEEL))
            consumeHeld(player, held, hand, false);

        // 提示里报的是这一次真正转换掉的数量(台面 + 并入的原料堆)
        player.displayClientMessage(
            Component.translatable("msg." + BetterWrenchMod.MODID + ".assemble.fan_done", batch.getCount()), true);
        return true;
    }

    /**
     * 手持物对应的<b>候选</b>鼓风处理类型列表(按序尝试, 取第一个既 {@code canProcess} 又有配方的);
     * 手持物不是这三种之一则返回空列表(调用方直接跳过本路径)。
     *
     * <p>打火石要看<b>置物台下方</b>的方块: 灵魂沙 / 灵魂土(原版 {@code BlockTags.SOUL_FIRE_BASE_BLOCKS},
     * 灵魂火就架在这两种方块上)或灵魂火本身, 先试缠魂、再回退烟熏 —— 灵魂底座上加工食物时
     * 缠魂本来就没有配方, 回退烟熏才不会占着路径不干活(2026-09-20)。</p>
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
     * 该方块是否属于「灵魂火底座」: 灵魂沙 / 灵魂土(vanilla tag), 外加灵魂火本身。
     *
     * <p>注意: 复审 B-17 —— 这是<b>全模组唯一</b>一份判据, {@code DepotSoulFlames}(锁定置物台的灵魂火焰
     * 粒子)也调用本方法, 不再各留一份拷贝(否则 Create 改了 tag 语义时会有一边不同步)。</p>
     */
    static boolean isSoulBase(Level level, BlockPos below) {
        BlockState state = level.getBlockState(below);
        return state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS) || state.is(Blocks.SOUL_FIRE);
    }

    /**
     * 鼓风处理成功时的<b>一次性反馈</b>: 原版音效 + 与 Create 鼓风机<b>同款</b>的粒子。
     *
     * <h2>音效(全部使用原版音效)</h2>
     * <ul>
     *   <li>水桶: {@link SoundEvents#BUCKET_EMPTY}(和原版倒水一样);</li>
     *   <li>岩浆桶: {@link SoundEvents#BUCKET_EMPTY_LAVA};</li>
     *   <li>打火石: {@link SoundEvents#FLINTANDSTEEL_USE}(音高照原版 {@code FlintAndSteelItem} 那样随机抖动)。</li>
     * </ul>
     * 本路径不再播拾取音({@link #playPickup}), 否则会与上面的音效叠在一起。
     *
     * <h2>粒子(照抄 Create 的 {@code FanProcessingType#spawnProcessingParticles})</h2>
     * 逐个类型核对过上游 {@code AllFanProcessingTypes} 里四个实现, 只保留<b>粒子种类与颜色</b>(含 y 偏移):
     * <ul>
     *   <li>洗涤 {@code SplashingType}(417-425 行): {@code DustParticleOptions(0x0055FF, 1)} + {@code SPIT}, 台面上方 0.5;</li>
     *   <li>冶炼 {@code BlastingType}(170-174 行): {@code LARGE_SMOKE}, 上方 0.25;</li>
     *   <li>烟熏 {@code SmokingType}(356-360 行): {@code POOF}, 上方 0.25;</li>
     *   <li>缠魂 {@code HauntingType}(236-246 行): {@code SOUL_FIRE_FLAME}(上方 0.45) + {@code SMOKE}
     *       (上游是 {@code random.nextInt(2) == 0} 的 1/2 概率, 这里按一半量级取定量 4 个)。</li>
     * </ul>
     * <p><b>两处刻意不同(其余照搬):</b></p>
     * <ol>
     *   <li>上游是<b>每 tick</b>调一次, 且自带 {@code random.nextInt(8) != 0} 就 return(1/8 概率)的门槛,
     *       而且用的是 {@code level.addParticle}(<b>只在客户端有效</b>, 服务端是空实现)。
     *       本模组是一次右击对应一次转换, 因此改成<b>服务端 {@code sendParticles} 喷一小撮</b>:
     *       附近所有玩家都能看见, 也无需新增网络包。</li>
     *   <li>上游靠 {@code (0, 1/16, 0)} 这类微小初速; 这个 API 只能给随机速度, 给不了固定向上初速,
     *       但这几种粒子本身就有上浮/扩散的物理(LARGE_SMOKE/POOF/SOUL_FIRE_FLAME 上浮, SPIT 受重力),
     *       观感与鼓风机一致, 因此不再为它绕道自定义网络包。</li>
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
     * <p>停留时长由<b>该玩家</b>在「加工」模式里用 Ctrl+滚轮选择的档位决定
     * ({@link com.nonono.createbetterwrench.mode.AssembleStay} 同步到 {@link DepotStayState},
     * 由客户端发包; 没有同步过就用默认档「中」= 4 tick)。</p>
     *
     * <p>先把成品摆上台面(经过 {@code notifyUpdate}, 客户端才看得见), 再由
     * {@link DepotProductEjector} 在停留时间到点后调 {@link #ejectHeldAndRefill}
     * 把它弹出去, 并清空台面、自动续料。</p>
     *
     * <p>注意: 这里<b>不</b>立刻续料 —— 续料发生在弹出那一刻, 目的就是让成品在台面上停留一下。</p>
     */
    private static void holdThenEject(Level level, BlockPos pos, DepotBlockEntity depot,
                                      ItemStack product, Player player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        DepotProductEjector.holdThenEject(serverLevel, pos, depot, product,
            DepotStayState.get(player.getUUID()), player.getUUID());
    }

    /**
     * 生成一个「加工产出」的<b>普通掉落物</b> —— 不打持久化标记、不 {@code setUnlimitedLifetime()},
     * 因此可正常拾取、也会像普通掉落物一样正常消失。
     *
     * <p>两种形态:</p>
     * <ul>
     *   <li>{@code launched == true}: 台面正上方的出口, 带向上的初速
     *       (保留从台面上弹出来的观感, 见 {@link #ejectHeldAndRefill});</li>
     *   <li>{@code launched == false}: 在给定锚点原地落下、无初速(注液批量产出用, 锚点取
     *       {@link #productDropPos})。</li>
     * </ul>
     */
    static void dropProduct(Level level, Vec3 anchor, ItemStack stack, boolean launched) {
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
     * 产出收集点: 置物台<b>东南侧</b>正上方(原「成品堆」所在的角落), 与西北角的原料堆分开,
     * 这样批量注液的产出不会和原料堆混在一处。
     */
    static Vec3 productDropPos(Level level, BlockPos pos) {
        double[] off = productOffset(level, pos);
        return new Vec3(pos.getX() + 0.5 + off[0], pos.getY() + 1.0, pos.getZ() + 0.5 + off[1]);
    }

    /**
     * 停留时间到: 把台面上那件成品<b>弹出</b>, 然后自动续上原料堆的下一个。
     * 只由 {@link DepotProductEjector} 调用。
     */
    static void ejectHeldAndRefill(ServerLevel level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty())
            return;
        // 弹出物是普通掉落物(带向上初速, 保留从台面弹出来的观感); 它落地后不会被置物台
        // 吸走, 因为下一行 consumeAndRefill 已经把台面占上了(占用时置物台拒收掉落物)。
        dropProduct(level, new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
            held.copy(), true);
        consumeAndRefill(level, pos, depot);       // 台面清空 + 立刻续下一个原料
    }

    /**
     * 解锁置物台时: 把<b>台面上正在加工的那一个</b>与<b>原料堆</b>全部<b>返还到玩家背包</b>
     * (设计约定, 2026-09-17: 解锁置物台后, 置物台上的全部物品(含掉落物堆)都返还给玩家)。
     *
     * <p>注意: 「成品堆」已不存在 —— 加工产出落在地上就是<b>普通掉落物</b>, 属于世界而不是这个置物台,
     * 所以本方法<b>不会</b>去捡已经弹出的成品, 它们留在原地等玩家拾取(也会正常消失)。</p>
     *
     * <p>装不下的部分由原版 {@code Inventory.placeItemBackInInventory} 掉在玩家脚下, <b>不会凭空消失</b>。</p>
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

    /**
     * 把成品<b>直接放进玩家背包</b>(2026-10-03 设计约定: 成品进背包, 半成品留在台面)。
     *
     * <p>装不下的部分由原版 {@code placeItemBackInInventory} 掉在玩家脚下, 因此不会丢失。
     * 多产出的路径无法让多件成品同时停在台面上, 因此它们不经过停留档位, 直接入包。</p>
     */
    static void giveToPlayer(Player player, ItemStack stack) {
        if (player == null || stack.isEmpty())
            return;
        player.getInventory().placeItemBackInInventory(stack.copy());
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
     * 消耗掉台面正在加工的那一个, 并<b>在同一 tick 内立刻续上原料堆的下一个</b>。
     *
     * <p>这就是「[加工] 的台面不会空着」的实现点: 台面一空就补料,
     * 于是玩家加工完一件就能直接接着下一件, 无需先右键放料再右键加工。</p>
     *
     * <p><b>续料还解决了一个顺序问题, 因此这两步不能拆开:</b>
     * 被弹出的成品是以掉落物实体形式在台面上方生成的, 要过几 tick 才落地。
     * 如果此时台面是空的, 它落地就会被置物台收进去;
     * 而本方法先把下一个原料顶上台面, 落地时台面是<b>占用</b>状态,
     * 普通置物台在占用时拒收掉落物({@link com.simibubi.create.content.logistics.depot.DepotBehaviour}
     * 的 {@code isOccupied()}), 成品因此会停在台面上而不被吸走。
     * 只有原料堆也空了(一个批次加工完)才会被收进去, 那时也正好该收工。</p>
     */
    static void consumeAndRefill(Level level, BlockPos pos, DepotBlockEntity depot) {
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

    static void consumeHeld(Player player, ItemStack held, InteractionHand hand, boolean keepHeld) {
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
