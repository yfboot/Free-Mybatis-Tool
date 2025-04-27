package com.tianlei.mybatis.provider;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CommonProcessors;
import com.intellij.util.xml.DomElement;
import com.tianlei.mybatis.dom.model.IdDomElement;
import com.tianlei.mybatis.service.JavaService;
import com.tianlei.mybatis.util.Icons;
import com.tianlei.mybatis.util.JavaUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

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
                    .setCellRenderer(new MapperXmlRenderer());
                result.add(builder.createLineMarkerInfo(((PsiNameIdentifierOwner) element).getNameIdentifier()));
            }
        }
    }
    
    // 使用DefaultPsiElementCellRenderer作为基类以避免兼容性问题
    private static class MapperXmlRenderer extends DefaultPsiElementCellRenderer {
        @Override
        public String getElementText(PsiElement element) {
            try {
                if (element instanceof XmlTag) {
                    XmlTag xmlTag = (XmlTag) element;
                    // 获取标签名称和id属性
                    String id = xmlTag.getAttributeValue("id");
                    String tagName = xmlTag.getName();
                    return tagName + " - " + (id != null ? id : "");
                }
            } catch (Exception e) {
                LOGGER.warn("Error getting element text", e);
            }
            return super.getElementText(element);
        }

        @Nullable
        @Override
        public String getContainerText(PsiElement element, String name) {
            try {
                // 显示文件路径
                if (element instanceof XmlTag) {
                    XmlTag xmlTag = (XmlTag) element;
                    if (xmlTag.getContainingFile() == null || !(xmlTag.getContainingFile() instanceof XmlFile)) {
                        return null;
                    }
                    
                    XmlFile xmlFile = (XmlFile) xmlTag.getContainingFile();
                    String fileName = xmlFile.getName();
                    
                    // 获取命名空间信息
                    String namespace = "";
                    XmlTag rootTag = xmlFile.getRootTag();
                    if (rootTag != null && "mapper".equals(rootTag.getName())) {
                        String namespaceAttr = rootTag.getAttributeValue("namespace");
                        if (namespaceAttr != null && !namespaceAttr.isEmpty()) {
                            namespace = " [" + namespaceAttr + "]";
                        }
                    }
                    
                    // 安全获取文件路径
                    String relativePath = "";
                    VirtualFile virtualFile = xmlFile.getVirtualFile();
                    if (virtualFile != null) {
                        String virtualFilePath = virtualFile.getPath();
                        if (element.getProject() != null && element.getProject().getBasePath() != null) {
                            String projectPath = element.getProject().getBasePath();
                            if (virtualFilePath.startsWith(projectPath)) {
                                relativePath = virtualFilePath.substring(projectPath.length());
                            } else {
                                relativePath = virtualFilePath;
                            }
                        } else {
                            relativePath = virtualFilePath;
                        }
                    }
                    
                    return fileName + namespace + (relativePath.isEmpty() ? "" : " - " + relativePath);
                }
            } catch (Exception e) {
                LOGGER.warn("Error getting container text", e);
            }
            return super.getContainerText(element, name);
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            // 确保调用父类的渲染方法
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }

        @Override
        protected int getIconFlags() {
            return 0;
        }
    }
}
