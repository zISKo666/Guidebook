package gigaherz.guidebook.guidebook.client;

import com.google.common.collect.Lists;
import gigaherz.guidebook.GuidebookMod;
import gigaherz.guidebook.guidebook.BookDocument;
import gigaherz.guidebook.guidebook.IBookGraphics;
import gigaherz.guidebook.guidebook.SectionRef;
import gigaherz.guidebook.guidebook.drawing.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class BookRendering implements IBookGraphics
{
    public static final int DEFAULT_BOOK_WIDTH = 276;
    public static final int DEFAULT_BOOK_HEIGHT = 198;
    public static final int DEFAULT_INNER_MARGIN = 22;
    public static final int DEFAULT_OUTER_MARGIN = 10;
    public static final int DEFAULT_BOTTOM_MARGIN = 8;
    public static final int DEFAULT_VERTICAL_MARGIN = 18;

    private BookDocument book;

    private double scaledWidthD;
    private double scaledHeightD;
    private int scaledWidth;
    private int scaledHeight;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final GuiGuidebook gui;

    private int bookWidth;
    private int bookHeight;
    private int innerMargin;
    private int outerMargin;
    private int verticalMargin;
    private int bottomMargin;
    private int pageWidth = bookWidth / 2 - innerMargin - outerMargin;
    private int pageHeight = bookHeight - verticalMargin;

    private final List<VisualChapter> chapters = Lists.newArrayList();
    private int lastProcessedChapter = 0;

    private final java.util.Stack<PageRef> history = new java.util.Stack<>();
    private int currentChapter = 0;
    private int currentPair = 0;
    private boolean hasScale;

    private float scalingFactor;

    private VisualElement previousHovering = null;

    BookRendering(BookDocument book, GuiGuidebook gui)
    {
        this.book = book;
        this.gui = gui;
    }

    @Override
    public Object owner()
    {
        return gui;
    }

    @Override
    public BookDocument getBook()
    {
        return book;
    }

    public void computeScaledResolution2(float scaleFactorCoef)
    {
        int width = mc.displayWidth;
        int height = mc.displayHeight;

        int scaleFactor = 1;
        double w = (DEFAULT_BOOK_WIDTH * 1.1) / scaleFactorCoef;
        double h = (DEFAULT_BOOK_HEIGHT * 1.1) / scaleFactorCoef;
        boolean flag = mc.isUnicode();
        int i = GuidebookMod.bookGUIScale < 0 ? mc.gameSettings.guiScale : GuidebookMod.bookGUIScale;

        if (i == 0)
        {
            i = 1000;
        }

        while (scaleFactor < i && width / (scaleFactor + 1) >= w && height / (scaleFactor + 1) >= h)
        {
            ++scaleFactor;
        }

        if (flag && scaleFactor % 2 != 0 && scaleFactor > 1)
        {
            --scaleFactor;
        }

        this.scaledWidthD = (double) width / (double) scaleFactor;
        this.scaledHeightD = (double) height / (double) scaleFactor;
        this.scaledWidth = MathHelper.ceil(scaledWidthD);
        this.scaledHeight = MathHelper.ceil(scaledHeightD);
    }

    @Override
    public void refreshScalingFactor()
    {
        float fontSize = book.getFontSize();

        if (MathHelper.epsilonEquals(fontSize, 1.0f))
        {
            this.hasScale = false;
            this.scalingFactor = 1.0f;
            this.scaledWidth = gui.width;
            this.scaledHeight = gui.height;

            this.bookWidth = DEFAULT_BOOK_WIDTH;
            this.bookHeight = DEFAULT_BOOK_HEIGHT;
            this.innerMargin = DEFAULT_INNER_MARGIN;
            this.outerMargin = DEFAULT_OUTER_MARGIN;
            this.verticalMargin = DEFAULT_VERTICAL_MARGIN;
            this.bottomMargin = DEFAULT_BOTTOM_MARGIN;
        }
        else
        {
            computeScaledResolution2(fontSize);

            this.hasScale = true;
            this.scalingFactor = Math.min(gui.width / (float) scaledWidth, gui.height / (float) scaledHeight);

            this.bookWidth = (int) (DEFAULT_BOOK_WIDTH / fontSize);
            this.bookHeight = (int) (DEFAULT_BOOK_HEIGHT / fontSize);
            this.innerMargin = (int) (DEFAULT_INNER_MARGIN / fontSize);
            this.outerMargin = (int) (DEFAULT_OUTER_MARGIN / fontSize);
            this.verticalMargin = (int) (DEFAULT_VERTICAL_MARGIN / fontSize);
            this.bottomMargin = (int) (DEFAULT_BOTTOM_MARGIN / fontSize);
        }

        this.pageWidth = this.bookWidth / 2 - this.innerMargin - this.outerMargin;
        this.pageHeight = this.bookHeight - this.verticalMargin - this.bottomMargin;
    }

    @Override
    public float getScalingFactor()
    {
        return scalingFactor;
    }

    private void pushHistory()
    {
        history.push(new PageRef(currentChapter, currentPair * 2));
    }

    @Override
    public boolean canGoNextPage()
    {
        return (getNextPair() >= 0 || canGoNextChapter());
    }

    @Override
    public void nextPage()
    {
        int pg = getNextPair();
        if (pg >= 0)
        {
            pushHistory();
            currentPair = pg;
        }
        else
        {
            nextChapter();
        }
    }

    @Override
    public boolean canGoPrevPage()
    {
        return getPrevPair() >= 0 || canGoPrevChapter();
    }

    @Override
    public void prevPage()
    {
        int pg = getPrevPair();
        if (pg >= 0)
        {
            pushHistory();
            currentPair = pg;
        }
        else
        {
            prevChapter(true);
        }
    }

    @Override
    public boolean canGoNextChapter()
    {
        return getNextChapter() >= 0;
    }

    @Override
    public void nextChapter()
    {
        int ch = getNextChapter();
        if (ch >= 0)
        {
            pushHistory();
            currentPair = 0;
            currentChapter = ch;
        }
    }

    @Override
    public boolean canGoPrevChapter()
    {
        return getPrevChapter() >= 0;
    }

    @Override
    public void prevChapter()
    {
        prevChapter(false);
    }

    private void prevChapter(boolean lastPage)
    {
        int ch = getPrevChapter();
        if (ch >= 0)
        {
            pushHistory();
            currentPair = 0;
            currentChapter = ch;
            if (lastPage) { currentPair = getVisualChapter(ch).totalPairs - 1; }
        }
    }

    @Override
    public boolean canGoBack()
    {
        return history.size() > 0;
    }

    @Override
    public void navigateBack()
    {
        if (history.size() > 0)
        {
            PageRef target = history.pop();
            //target.resolve(book);
            currentChapter = target.chapter;
            currentPair = target.page / 2;
        }
        else
        {
            currentChapter = 0;
            currentPair = 0;
        }
    }

    @Override
    public void navigateHome()
    {
        if (book.home != null)
        {
            navigateTo(book.home);
        }
    }

    private int getNextChapter()
    {
        for (int i = currentChapter + 1; i < book.chapterCount(); i++)
        {
            if (needChapter(i))
                return i;
        }
        return -1;
    }

    private int getPrevChapter()
    {
        for (int i = currentChapter - 1; i >= 0; i--)
        {
            if (needChapter(i))
                return i;
        }
        return -1;
    }

    private int getNextPair()
    {
        VisualChapter ch = getVisualChapter(currentChapter);
        if (currentPair + 1 >= ch.totalPairs)
            return -1;
        return currentPair + 1;
    }

    private int getPrevPair()
    {
        if (currentPair - 1 < 0)
            return -1;
        return currentPair - 1;
    }

    private boolean needChapter(int chapterNumber)
    {
        if (chapterNumber < 0 || chapterNumber >= book.chapterCount())
            return false;
        BookDocument.ChapterData ch = book.getChapter(chapterNumber);
        return ch.conditionResult && !ch.isEmpty();
    }

    private boolean needSection(int chapterNumber, int sectionNumber)
    {
        BookDocument.ChapterData ch = book.getChapter(chapterNumber);
        if (sectionNumber < 0 || sectionNumber >= ch.sections.size())
            return false;
        BookDocument.PageData section = ch.sections.get(sectionNumber);
        return section.conditionResult && !section.isEmpty();
    }

    private int findSectionStart(SectionRef ref)
    {
        VisualChapter vc = getVisualChapter(currentChapter);
        for (int i = 0; i < vc.pages.size(); i++)
        {
            VisualPage page = vc.pages.get(i);
            if (page.ref.section == ref.section)
                return i / 2;

            if (page.ref.section > ref.section)
                return 0; // give up
        }
        return 0;
    }

    @Override
    public void navigateTo(final SectionRef target)
    {
        if (!target.resolve(book))
            return;
        pushHistory();

        if (!needChapter(target.chapter))
            return;

        if (!needSection(target.chapter, target.section))
            return;

        currentChapter = target.chapter;

        currentPair = findSectionStart(target);
    }

    private VisualChapter getVisualChapter(int chapter)
    {
        while (chapters.size() <= chapter && lastProcessedChapter < book.chapterCount())
        {
            BookDocument.ChapterData bc = book.getChapter(lastProcessedChapter++);
            if (!bc.conditionResult)
                continue;

            VisualChapter ch = new VisualChapter();
            if (chapters.size() > 0)
            {
                VisualChapter prev = chapters.get(chapters.size() - 1);
                ch.startPair = prev.startPair + prev.totalPairs;
            }

            Size pageSize = new Size(pageWidth, pageHeight);
            bc.reflow(this, ch, pageSize);

            ch.totalPairs = (ch.pages.size() + 1) / 2;
            chapters.add(ch);
        }

        if (chapter >= chapters.size())
        {
            VisualChapter vc = new VisualChapter();
            vc.pages.add(new VisualPage(new SectionRef(chapter, 0)));
            return vc;
        }

        return chapters.get(chapter);
    }

    @Override
    public int addString(int left, int top, String s, int color, float scale)
    {
        FontRenderer fontRenderer = gui.getFontRenderer();

        // Does scaling need to be performed?
        if (!(MathHelper.epsilonEquals(scale, 1.0f)))
        {
            GlStateManager.pushMatrix();
            {
                GlStateManager.translate(left, top, 0);
                GlStateManager.scale(scale, scale, 1f);
                fontRenderer.drawString(s, 0, 0, color);
            }
            GlStateManager.popMatrix();
        }
        else
        {
            fontRenderer.drawString(s, left, top, color);
        }

        return fontRenderer.FONT_HEIGHT;
    }

    @Override
    public boolean mouseClicked(int mouseButton)
    {
        Minecraft mc = Minecraft.getMinecraft();
        int dw = scaledWidth;
        int dh = scaledHeight;
        int mouseX = Mouse.getX() * dw / mc.displayWidth;
        int mouseY = dh - Mouse.getY() * dh / mc.displayHeight;

        if (mouseButton == 0)
        {
            VisualChapter ch = getVisualChapter(currentChapter);

            final VisualPage pgLeft = ch.pages.get(currentPair * 2);

            if (mouseClickPage(mouseX, mouseY, pgLeft, true))
                return true;

            if (currentPair * 2 + 1 < ch.pages.size())
            {
                final VisualPage pgRight = ch.pages.get(currentPair * 2 + 1);

                if (mouseClickPage(mouseX, mouseY, pgRight, false))
                    return true;
            }
        }

        return false;
    }

    private boolean mouseClickPage(int mX, int mY, VisualPage pg, boolean isLeftPage)
    {
        Point offset = getPageOffset(isLeftPage);
        mX -= offset.x;
        mY -= offset.y;
        for (VisualElement e : pg.children)
        {
            if (mX >= e.position.x && mX <= (e.position.x + e.size.width) &&
                    mY >= e.position.y && mY <= (e.position.y + e.size.height))
            {
                e.click(this);
                return true;
            }
        }
        return false;
    }


    @Override
    public boolean mouseHover(int mouseX, int mouseY)
    {
        VisualChapter ch = getVisualChapter(currentChapter);

        final VisualPage pgLeft = ch.pages.get(currentPair * 2);

        VisualElement hovering = mouseHoverPage(pgLeft, true);

        if (hovering == null)
        {
            if (currentPair * 2 + 1 < ch.pages.size())
            {
                final VisualPage pgRight = ch.pages.get(currentPair * 2 + 1);

                hovering = mouseHoverPage(pgRight, false);
            }
        }

        if (hovering != previousHovering && previousHovering != null)
        {
            previousHovering.mouseOut(this, mouseX, mouseY);
        }
        previousHovering = hovering;

        if (hovering != null)
        {
            hovering.mouseOver(this, mouseX, mouseY);
            return true;
        }

        return false;
    }

    @Nullable
    private VisualElement mouseHoverPage(VisualPage pg, boolean isLeftPage)
    {
        Minecraft mc = Minecraft.getMinecraft();
        int dw = scaledWidth;
        int dh = scaledHeight;
        int mX = Mouse.getX() * dw / mc.displayWidth;
        int mY = dh - Mouse.getY() * dh / mc.displayHeight;
        Point offset = getPageOffset(isLeftPage);

        mX -= offset.x;
        mY -= offset.y;

        for (VisualElement e : pg.children)
        {
            if (mX >= e.position.x && mX <= (e.position.x + e.size.width) &&
                    mY >= e.position.y && mY <= (e.position.y + e.size.height))
            {
                if (e.wantsHover())
                    return e;
            }
        }

        return null;
    }

    @Override
    public void drawCurrentPages()
    {
        if (hasScale)
        {
            GlStateManager.pushMatrix();
            GlStateManager.scale(scalingFactor, scalingFactor, scalingFactor);
        }

        drawPage(currentPair * 2);
        drawPage(currentPair * 2 + 1);

        if (hasScale)
        {
            GlStateManager.popMatrix();
        }
    }

    private Point getPageOffset(boolean leftPage)
    {
        int left = scaledWidth / 2 - pageWidth - innerMargin;
        int right = scaledWidth / 2 + innerMargin;
        int top = (scaledHeight - pageHeight) / 2 - bottomMargin;

        return new Point(leftPage ? left : right, top);
    }

    private void drawPage(int page)
    {
        VisualChapter ch = getVisualChapter(currentChapter);
        if (page >= ch.pages.size())
            return;

        VisualPage pg = ch.pages.get(page);

        Point offset = getPageOffset((page & 1) == 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(offset.x, offset.y, 0);

        for (VisualElement e : pg.children)
        {
            e.draw(this);
        }

        String cnt = String.valueOf(ch.startPair * 2 + page + 1);
        Size sz = measure(cnt);

        addString((pageWidth - sz.width) / 2, pageHeight + 2, cnt, 0xFF000000, 1.0f);

        GlStateManager.popMatrix();
    }

    @Override
    public void drawItemStack(int left, int top, int z, ItemStack stack, int color, float scale)
    {
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();

        GlStateManager.pushMatrix();
        GlStateManager.translate(left, top, z);
        GlStateManager.scale(scale, scale, scale);

        RenderHelper.enableGUIStandardItemLighting();
        gui.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        RenderHelper.disableStandardItemLighting();

        gui.mc.getRenderItem().renderItemOverlayIntoGUI(gui.getFontRenderer(), stack, 0, 0, null);

        GlStateManager.popMatrix();

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
    }

    @Override
    public void drawImage(ResourceLocation loc, int x, int y, int tx, int ty, int w, int h, int tw, int th, float scale)
    {
        int sw = tw != 0 ? tw : 256;
        int sh = th != 0 ? th : 256;

        if (w == 0) w = sw;
        if (h == 0) h = sh;

        ResourceLocation locExpanded = new ResourceLocation(loc.getNamespace(), "textures/" + loc.getPath() + ".png");
        gui.getRenderEngine().bindTexture(locExpanded);

        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        drawFlexible(x, y, tx, ty, w, h, sw, sh, scale);
    }

    private static void drawFlexible(int x, int y, float tx, float ty, int w, int h, int tw, int th, float scale)
    {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        float hs = h * scale;
        float ws = w * scale;
        float tsw = 1.0f / tw;
        float tsh = 1.0f / th;
        bufferbuilder
                .pos(x, y + hs, 0.0D)
                .tex(tx * tsw, (ty + h) * tsh)
                .endVertex();
        bufferbuilder
                .pos(x + ws, y + hs, 0.0D)
                .tex((tx + w) * tsw, (ty + h) * tsh)
                .endVertex();
        bufferbuilder
                .pos(x + ws, y, 0.0D)
                .tex((tx + w) * tsw, ty * tsh)
                .endVertex();
        bufferbuilder
                .pos(x, y, 0.0D)
                .tex(tx * tsw, ty * tsh)
                .endVertex();
        tessellator.draw();
    }

    @Override
    public void drawTooltip(ItemStack stack, int x, int y)
    {
        gui.drawTooltip(stack, x, y);
    }

    @Override
    public Size measure(String text)
    {
        FontRenderer font = gui.getFontRenderer();
        int width = font.getStringWidth(text);
        return new Size(width, font.FONT_HEIGHT);
    }

    @Override
    public List<VisualElement> measure(String text, int width, int firstLineWidth, float scale, int position, float baseline, int verticalAlignment)
    {
        FontRenderer font = gui.getFontRenderer();
        List<VisualElement> sizes = Lists.newArrayList();
        TextMetrics.wrapFormattedStringToWidth(font, (s) -> {
            int width2 = font.getStringWidth(s);
            sizes.add(new VisualText(s, new Size((int) (width2 * scale), (int) (font.FONT_HEIGHT * scale)), position, baseline, verticalAlignment, scale));
        }, text, width, firstLineWidth, true);
        return sizes;
    }

    @Override
    public int getActualBookHeight()
    {
        return bookHeight;
    }

    @Override
    public int getActualBookWidth()
    {
        return bookWidth;
    }

    private static class TextMetrics
    {
        private static boolean isFormatColor(char colorChar)
        {
            return colorChar >= '0' && colorChar <= '9' || colorChar >= 'a' && colorChar <= 'f' || colorChar >= 'A' && colorChar <= 'F';
        }

        private static int sizeStringToWidth(FontRenderer font, String str, int wrapWidth)
        {
            int i = str.length();
            int j = 0;
            int k = 0;
            int l = -1;

            for (boolean flag = false; k < i; ++k)
            {
                char c0 = str.charAt(k);

                switch (c0)
                {
                    case '\n':
                        --k;
                        break;
                    case ' ':
                        l = k;
                    default:
                        j += font.getCharWidth(c0);

                        if (flag)
                        {
                            ++j;
                        }

                        break;
                    case '\u00a7':

                        if (k < i - 1)
                        {
                            ++k;
                            char c1 = str.charAt(k);

                            if (c1 != 'l' && c1 != 'L')
                            {
                                if (c1 == 'r' || c1 == 'R' || isFormatColor(c1))
                                {
                                    flag = false;
                                }
                            }
                            else
                            {
                                flag = true;
                            }
                        }
                }

                if (c0 == '\n')
                {
                    ++k;
                    l = k;
                    break;
                }

                if (j > wrapWidth)
                {
                    break;
                }
            }

            return k != i && l != -1 && l < k ? l : k;
        }

        private static void wrapFormattedStringToWidth(FontRenderer font, Consumer<String> dest, String str, int wrapWidth, int wrapWidthFirstLine, boolean firstLine)
        {
            int i = sizeStringToWidth(font, str, firstLine ? wrapWidthFirstLine : wrapWidth);

            if (str.length() <= i)
            {
                dest.accept(str);
            }
            else
            {
                String s = str.substring(0, i);
                dest.accept(s);
                dest.accept("\n"); // line break
                char c0 = str.charAt(i);
                boolean flag = c0 == ' ' || c0 == '\n';
                String s1 = FontRenderer.getFormatFromString(s) + str.substring(i + (flag ? 1 : 0));
                wrapFormattedStringToWidth(font, dest, s1, wrapWidth, wrapWidthFirstLine, false);
            }
        }
    }

    private class PageRef
    {
        public int chapter;
        public int page;

        public PageRef(int currentChapter, int currentPage)
        {
            chapter = currentChapter;
            page = currentPage;
        }
    }
}
