package com.tianlei.mybatis.provider;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.DomElement;
import com.tianlei.mybatis.dom.model.IdDomElement;
import com.tianlei.mybatis.service.JavaService;
import com.tianlei.mybatis.util.Icons;
import com.tianlei.mybatis.util.JavaUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JList;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapperLineMarkerProvider extends RelatedItemLineMarkerProvider {

    private static final Logger LOGGER = Logger.getInstance(MapperLineMarkerProvider.class);
    private static final Function<DomElement, XmlTag> FUN = domElement -> domElement.getXmlTag();

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element instanceof PsiNameIdentifierOwner && JavaUtils.isElementWithinInterface(element)) {
            CommonProcessors.CollectProcessor<IdDomElement> processor = new CommonProcessors.CollectProcessor<>();
            JavaService.getInstance(element.getProject()).process(element, processor);
            Collection<IdDomElement> results = processor.getResults();
            if (!results.isEmpty()) {
                NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                        .create(Icons.MAPPER_LINE_MARKER_ICON)
                        .setTargets(Collections2.transform(results, FUN))
                        .setTooltipText("Navigate to Mapper XML")
                        .setPopupTitle("Select Target Mapper")
                        .setCellRenderer(new MapperCellRenderer());
                result.add(builder.createLineMarkerInfo(((PsiNameIdentifierOwner) element).getNameIdentifier()));
            }
        }
    }

    private static class MapperCellRenderer extends PsiElementListCellRenderer<PsiElement> {
        // 保存所有文件的路径，用于比较
        private static final List<String> allPaths = new ArrayList<>();
        // 缓存已经处理过的路径对应的差异部分
        private static final Map<String, List<PathSegment>> pathDifferenceCache = new HashMap<>();
        // 用于给不同的差异段落分配不同颜色
        private static final Map<String, Integer> segmentColorMap = new HashMap<>();

        // 路径段落类，记录路径中的一段文本及其是否为差异部分
        private static class PathSegment {
            String text;
            boolean isDifferent;
            String signature; // 用于颜色一致性的标识

            PathSegment(String text, boolean isDifferent, String signature) {
                this.text = text;
                this.isDifferent = isDifferent;
                this.signature = signature;
            }
        }

        // 存储每个元素的方法名
        private String currentMethodName = "";

        @Override
        public String getElementText(PsiElement element) {
            try {
                if (element instanceof XmlTag) {
                    XmlTag xmlTag = (XmlTag) element;
                    // 保存方法名以供后续使用
                    String id = xmlTag.getAttributeValue("id");
                    currentMethodName = id != null ? id : "";
                    return ""; // 返回空字符串，在自定义组件中显示方法名和路径
                }
            } catch (Exception e) {
                LOGGER.warn("Error getting element text", e);
            }
            return "";
        }

        @Nullable
        @Override
        protected String getContainerText(PsiElement element, String name) {
            try {
                // 显示文件路径
                if (element instanceof XmlTag) {
                    XmlTag xmlTag = (XmlTag) element;
                    if (xmlTag.getContainingFile() == null || !(xmlTag.getContainingFile() instanceof XmlFile)) {
                        return null;
                    }

                    XmlFile xmlFile = (XmlFile) xmlTag.getContainingFile();

                    // 安全获取文件路径
                    String relativePath = "";
                    VirtualFile virtualFile = xmlFile.getVirtualFile();

                    if (virtualFile != null) {
                        String virtualFilePath = virtualFile.getPath();
                        if (element.getProject() != null && element.getProject().getBasePath() != null) {
                            String projectPath = element.getProject().getBasePath();
                            if (virtualFilePath.startsWith(projectPath)) {
                                relativePath = virtualFilePath.substring(projectPath.length());
                                // 删除路径开头的 / 或 \
                                if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                                    relativePath = relativePath.substring(1);
                                }
                            } else {
                                relativePath = virtualFilePath;
                            }
                        } else {
                            relativePath = virtualFilePath;
                        }

                        // 添加到路径列表，用于后续比较差异
                        synchronized (allPaths) {
                            if (!allPaths.contains(relativePath)) {
                                allPaths.add(relativePath);
                                // 当添加新路径时清除缓存，重新计算差异
                                pathDifferenceCache.clear();
                                segmentColorMap.clear();
                            }
                        }
                    }

                    // 返回路径
                    return relativePath;
                }
            } catch (Exception e) {
                LOGGER.warn("Error getting container text", e);
            }
            return null;
        }

        @Override
        protected int getIconFlags() {
            return 0;
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            // 移除默认的中间和右侧组件
            removeAll();
            setLayout(new BorderLayout());

            // 设置背景色
            setBackground(isSelected ?
                    UIUtil.getListSelectionBackground(true) :
                    UIUtil.getListBackground());

            if (value instanceof PsiElement) {
                PsiElement element = (PsiElement) value;

                // 获取方法名和路径
                getElementText(element); // 这将设置currentMethodName
                String containerText = getContainerText(element, "");

                // 创建一个完整的彩色组件来显示方法名和路径
                SimpleColoredComponent mainComponent = new SimpleColoredComponent();
                mainComponent.setOpaque(true);
                mainComponent.setBackground(isSelected ?
                        UIUtil.getListSelectionBackground(true) :
                        UIUtil.getListBackground());

                // 设置图标
                mainComponent.setIcon(getIcon(element));

                // 添加方法名（如果有）
                if (!currentMethodName.isEmpty()) {
                    // 方法名使用一个固定的宽度显示，后面跟着路径
                    mainComponent.append(currentMethodName,
                            new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD,
                                    isSelected ? UIUtil.getListSelectionForeground(true) :
                                            list.getForeground()));

                    // 添加适当的空格，确保路径左对齐
                    mainComponent.append("   ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }

                if (containerText != null && !containerText.isEmpty()) {
                    // 获取路径差异部分
                    List<PathSegment> segments = getPathDifferenceSegments(containerText);

                    // 定义几种颜色用于高亮差异部分
                    Color[] highlightColors = {
                            new Color(0, 102, 204),    // 蓝色
                            new Color(51, 153, 102),   // 绿色
                            new Color(230, 92, 0),     // 橙色
                            new Color(153, 51, 153),   // 紫色
                            new Color(204, 0, 0),      // 红色
                            new Color(0, 153, 153),    // 青色
                            new Color(0, 51, 102),     // 深蓝色
                            new Color(102, 0, 102)     // 深紫色
                    };

                    // 为每个段落应用样式
                    for (PathSegment segment : segments) {
                        if (segment.isDifferent) {
                            // 对差异部分使用不同颜色高亮，确保相同值使用相同颜色
                            int colorIndex = getColorIndexForSegment(segment.signature);
                            Color color = highlightColors[colorIndex % highlightColors.length];

                            mainComponent.append(segment.text,
                                    new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD,
                                            isSelected ? UIUtil.getListSelectionForeground(true) : color));
                        } else {
                            // 非差异部分使用灰色
                            mainComponent.append(segment.text,
                                    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                            isSelected ? UIUtil.getListSelectionForeground(true) :
                                                    SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor()));
                        }
                    }
                }

                // 添加主组件
                add(mainComponent, BorderLayout.CENTER);
            }

            return this;
        }

        /**
         * 为段落获取颜色索引，确保相同内容使用相同颜色
         */
        private int getColorIndexForSegment(String signature) {
            if (!segmentColorMap.containsKey(signature)) {
                // 分配一个新的颜色索引
                segmentColorMap.put(signature, segmentColorMap.size());
            }
            return segmentColorMap.get(signature);
        }

        /**
         * 获取路径中的差异段落
         *
         * @param path 当前路径
         * @return 路径段落列表，标记了哪些部分是差异部分
         */
        private List<PathSegment> getPathDifferenceSegments(String path) {
            // 检查缓存中是否已有处理结果
            if (pathDifferenceCache.containsKey(path)) {
                return pathDifferenceCache.get(path);
            }

            // 创建结果列表
            List<PathSegment> segments = new ArrayList<>();

            // 解析实际路径为段落
            String[] folders = path.split("[/\\\\]");

            // 如果只有一个路径，则没有比较对象，全部视为非差异
            if (allPaths.size() <= 1) {
                segments.add(new PathSegment(path, false, "single_path"));
                pathDifferenceCache.put(path, segments);
                return segments;
            }

            // 找出所有需要比较的路径（不包括当前路径）
            List<String> processedOtherPaths = new ArrayList<>();
            for (String otherPath : allPaths) {
                if (!otherPath.equals(path)) {
                    processedOtherPaths.add(otherPath);
                }
            }

            // 实际路径比较
            List<String[]> otherPathFolders = new ArrayList<>();
            for (String otherPath : processedOtherPaths) {
                otherPathFolders.add(otherPath.split("[/\\\\]"));
            }

            // 构建路径段落，标记差异部分
            StringBuilder currentSegment = new StringBuilder();
            boolean isDifferent = false;
            String segmentSignature = "";

            // 处理每个文件夹
            for (int i = 0; i < folders.length; i++) {
                String folder = folders[i];

                // 检查此段是否与其他路径不同
                boolean different = false;
                for (String[] otherFolders : otherPathFolders) {
                    if (i >= otherFolders.length || !folder.equals(otherFolders[i])) {
                        different = true;
                        break;
                    }
                }

                // 如果当前差异状态变化，添加之前的段落并重置
                if ((different != isDifferent && currentSegment.length() > 0) ||
                        (different && isDifferent && !folder.equals(segmentSignature))) {
                    segments.add(new PathSegment(currentSegment.toString(), isDifferent, segmentSignature));
                    currentSegment = new StringBuilder();
                    segmentSignature = folder;
                }

                // 更新当前差异状态
                isDifferent = different;
                if (isDifferent) {
                    segmentSignature = folder;
                }

                // 添加当前文件夹名称和分隔符到当前段落
                currentSegment.append(folder);
                if (i < folders.length - 1) {
                    currentSegment.append("/");
                }
            }

            // 添加最后一个段落
            if (currentSegment.length() > 0) {
                segments.add(new PathSegment(currentSegment.toString(), isDifferent, segmentSignature));
            }

            // 缓存结果
            pathDifferenceCache.put(path, segments);
            return segments;
        }
    }
}
