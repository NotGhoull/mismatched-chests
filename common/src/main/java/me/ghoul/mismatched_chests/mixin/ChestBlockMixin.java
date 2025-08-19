package me.ghoul.mismatched_chests.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiPredicate;

@Mixin(ChestBlock.class)
public class ChestBlockMixin {
    @Unique
    private boolean mismatchedChests$isValidChest(BlockState block) { return block.getBlock() instanceof ChestBlock; }

    @Unique
    private boolean mismatchedChests$isValidChestEntity(BlockEntity block) { return block instanceof ChestBlockEntity; }

    @Redirect(
            method = {"candidatePartnerFacing", "updateShape"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"
            )
    )
    private boolean onIsChestBlock(BlockState instance, Block other) {
        return mismatchedChests$isValidChest(instance);
    }

    @Inject(method = "combine", at = @At("HEAD"), cancellable = true)
    public void onCombine(BlockState blockState, Level level, BlockPos pos, boolean bl, CallbackInfoReturnable<DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity>> cir) {
        BiPredicate<LevelAccessor, BlockPos> predicate = bl ? (levelAccessor, blockPos1) -> false : ChestBlock::isChestBlockedAt;

        // Get entity, and preform checks
        BlockEntity thisEntity = level.getBlockEntity(pos);
        if (!mismatchedChests$isValidChestEntity(thisEntity) || predicate.test(level, pos)) {
            cir.setReturnValue(DoubleBlockCombiner.Combiner::acceptNone);
            return;
        }

        ChestBlockEntity thisChestEntity = (ChestBlockEntity) thisEntity;

        // Check for neighbors in all 4 directions
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            BlockEntity neighborEntity = level.getBlockEntity(neighborPos);

            if (mismatchedChests$isValidChest(neighborState) && mismatchedChests$isValidChestEntity(neighborEntity) && ChestBlock.getConnectedDirection(neighborState) == direction.getOpposite()) {
                ChestBlockEntity neighbourChestEntity = (ChestBlockEntity) neighborEntity;
                cir.setReturnValue(combineChests(thisChestEntity, neighbourChestEntity, blockState));
                return;
            }
        }

        // If no combinable neighbor is found, return a single chest
        cir.setReturnValue(new DoubleBlockCombiner.NeighborCombineResult.Single<>(thisChestEntity));
    }

    @Unique
    /// Combine two ChestBlockEntities into a double chest.
    private DoubleBlockCombiner.NeighborCombineResult<ChestBlockEntity> combineChests(ChestBlockEntity entity1, ChestBlockEntity entity2, BlockState blockState) {
        if (blockState.getValue(ChestBlock.TYPE) == ChestType.LEFT) {
            return new DoubleBlockCombiner.NeighborCombineResult.Double<>(entity1, entity2);
        } else {
            return new DoubleBlockCombiner.NeighborCombineResult.Double<>(entity2, entity1);
        }
    }

}
