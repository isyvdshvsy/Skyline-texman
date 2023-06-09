// SPDX-License-Identifier: MPL-2.0
// Copyright © 2023 Skyline Team and Contributors (https://github.com/skyline-emu/)

#pragma once

#include <array>
#include <vulkan/vulkan_raii.hpp>
#include "types.h"

namespace nnvk {
    /**
     * @note Underscores are used here to make the enum values more readable
     */
    enum class Format : i32 {
        None,
        R8,
        R8SN,
        R8UI,
        R8I,
        R16F,
        R16,
        R16SN,
        R16UI,
        R16I,
        R32F,
        R32UI,
        R32I,
        RG8,
        RG8SN,
        RG8UI,
        RG8I,
        RG16F,
        RG16,
        RG16SN,
        RG16UI,
        RG16I,
        RG32F,
        RG32UI,
        RG32I,
        RGB8,
        RGB8SN,
        RGB8UI,
        RGB8I,
        RGB16F,
        RGB16,
        RGB16SN,
        RGB16UI,
        RGB16I,
        RGB32F,
        RGB32UI,
        RGB32I,
        RGBA8,
        RGBA8SN,
        RGBA8UI,
        RGBA8I,
        RGBA16F,
        RGBA16,
        RGBA16SN,
        RGBA16UI,
        RGBA16I,
        RGBA32F,
        RGBA32UI,
        RGBA32I,
        Stencil8,
        Depth16,
        Depth24,
        Depth32F,
        Depth24_Stencil8,
        Depth32F_Stencil8,
        RGBX8_SRGB,
        RGBA8_SRGB,
        RGBA4,
        RGB5,
        RGB5A1,
        RGB565,
        RGB10A2,
        RGB10A2UI,
        R11G11B10F,
        RGB9E5F,
        RGB_DXT1,
        RGBA_DXT1,
        RGBA_DXT3,
        RGBA_DXT5,
        RGB_DXT1_SRGB,
        RGBA_DXT1_SRGB,
        RGBA_DXT3_SRGB,
        RGBA_DXT5_SRGB,
        RGTC1_Unorm,
        RGTC1_Snorm,
        RGTC2_Unorm,
        RGTC2_Snorm,
        BPTC_Unorm,
        BPTC_Unorm_SRGB,
        BPTC_SFLOAT,
        BPTC_UFLOAT,
        R8_UI2F,
        R8_I2F,
        R16_UI2F,
        R16_I2F,
        R32_UI2F,
        R32_I2F,
        RG8_UI2F,
        RG8_I2F,
        RG16_UI2F,
        RG16_I2F,
        RG32_UI2F,
        RG32_I2F,
        RGB8_UI2F,
        RGB8_I2F,
        RGB16_UI2F,
        RGB16_I2F,
        RGB32_UI2F,
        RGB32_I2F,
        RGBA8_UI2F,
        RGBA8_I2F,
        RGBA16_UI2F,
        RGBA16_I2F,
        RGBA32_UI2F,
        RGBA32_I2F,
        RGB10A2SN,
        RGB10A2I,
        RGB10A2_UI2F,
        RGB10A2_I2F,
        RGBX8,
        RGBX8SN,
        RGBX8UI,
        RGBX8I,
        RGBX16F,
        RGBX16,
        RGBX16SN,
        RGBX16UI,
        RGBX16I,
        RGBX32F,
        RGBX32UI,
        RGBX32I,
        RGBA_ASTC_4x4,
        RGBA_ASTC_5x4,
        RGBA_ASTC_5x5,
        RGBA_ASTC_6x5,
        RGBA_ASTC_6x6,
        RGBA_ASTC_8x5,
        RGBA_ASTC_8x6,
        RGBA_ASTC_8x8,
        RGBA_ASTC_10x5,
        RGBA_ASTC_10x6,
        RGBA_ASTC_10x8,
        RGBA_ASTC_10x10,
        RGBA_ASTC_12x10,
        RGBA_ASTC_12x12,
        RGBA_ASTC_4x4_SRGB,
        RGBA_ASTC_5x4_SRGB,
        RGBA_ASTC_5x5_SRGB,
        RGBA_ASTC_6x5_SRGB,
        RGBA_ASTC_6x6_SRGB,
        RGBA_ASTC_8x5_SRGB,
        RGBA_ASTC_8x6_SRGB,
        RGBA_ASTC_8x8_SRGB,
        RGBA_ASTC_10x5_SRGB,
        RGBA_ASTC_10x6_SRGB,
        RGBA_ASTC_10x8_SRGB,
        RGBA_ASTC_10x10_SRGB,
        RGBA_ASTC_12x10_SRGB,
        RGBA_ASTC_12x12_SRGB,
        BGR565,
        BGR5,
        BGR5A1,
        A1BGR5,
        BGRX8,
        BGRA8,
        BGRX8_SRGB,
        BGRA8_SRGB,
        FormatSize
    };

    namespace format {
        struct FormatInfo {
            bool texture;
            bool vertex;
            u8 bytesPerBlock;
            u8 blockWidth;
            u8 blockHeight;
            u8 redBits;
            u8 greenBits;
            u8 blueBits;
            u8 alphaBits;
            u8 depthBits;
            u8 stencilBits;
            vk::Format vkFormat;

            bool IsCompressed() const {
                return blockWidth > 1 || blockHeight > 1;
            }

            bool IsDepthStencil() const {
                return depthBits > 0 || stencilBits > 0;
            }
        };

        extern const std::array<FormatInfo, static_cast<size_t>(Format::FormatSize)> FormatProperties;

        inline FormatInfo GetFormatInfo(Format format) {
            return FormatProperties[static_cast<size_t>(format)];
        }
    }
}