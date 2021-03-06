package gigaherz.guidebook.guidebook.elements;

import com.google.common.collect.Lists;
import gigaherz.guidebook.guidebook.IBookGraphics;
import gigaherz.guidebook.guidebook.IConditionSource;
import gigaherz.guidebook.guidebook.SectionRef;
import gigaherz.guidebook.guidebook.drawing.VisualElement;
import gigaherz.guidebook.guidebook.drawing.VisualLink;
import gigaherz.guidebook.guidebook.drawing.VisualText;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.List;

public class ElementLink extends ElementSpan
{
    public String textTarget;
    public String textAction;
    public SectionRef target;
    public int colorHover = 0xFF77cc66;

    public ElementLink(String text)
    {
        super(text, true, true);
        underline = true;
        color = 0xFF7766cc;
    }

    @Override
    public List<VisualElement> measure(IBookGraphics nav, int width, int firstLineWidth)
    {
        List<VisualElement> texts = super.measure(nav, width, firstLineWidth);
        List<VisualElement> links = Lists.newArrayList();
        VisualLink.SharedHoverContext ctx = null;
        for (VisualElement e : texts)
        {
            VisualElement el = e;
            if (e instanceof VisualText)
            {
                VisualText text = (VisualText) e;

                VisualLink link = new VisualLink(text.text, text.size, position, baseline, verticalAlignment, scale);
                if (ctx == null) ctx = link.hoverContext;
                else link.hoverContext = ctx;
                link.color = color;
                link.target = target;
                link.colorHover = colorHover;
                link.textTarget = textTarget;
                link.textAction = textAction;
                el = link;
            }
            // else TODO: clickable images in links
            links.add(el);
        }
        return links;
    }

    @Override
    public void parse(IConditionSource book, NamedNodeMap attributes)
    {
        super.parse(book, attributes);

        Node attr = attributes.getNamedItem("ref");
        if (attr != null)
        {
            String ref = attr.getTextContent();
            target = SectionRef.fromString(ref);
        }

        attr = attributes.getNamedItem("href");
        if (attr != null)
        {
            textTarget = attr.getTextContent();
            textAction = "openUrl";
        }

        attr = attributes.getNamedItem("text");
        if (attr != null)
        {
            textTarget = attr.getTextContent();
        }

        attr = attributes.getNamedItem("action");
        if (attr != null)
        {
            textAction = attr.getTextContent();
        }
    }

    @Override
    public Element copy()
    {
        ElementLink link = super.copy(new ElementLink(text));
        link.color = color;
        link.bold = bold;
        link.italics = italics;
        link.underline = underline;

        link.target = target.copy();
        link.colorHover = colorHover;
        link.textTarget = textTarget;
        link.textAction = textAction;

        return link;
    }
}
