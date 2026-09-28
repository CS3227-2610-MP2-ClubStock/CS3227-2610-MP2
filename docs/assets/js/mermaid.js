// Jekyll emits fenced Mermaid definitions as code blocks, not diagrams.
const blocks = document.querySelectorAll('pre > code.language-mermaid, .language-mermaid pre > code');

if (blocks.length > 0) {
  try {
    const { default: mermaid } = await import(
      'https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs'
    );
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' });

    for (const [index, block] of blocks.entries()) {
      try {
        const { svg, bindFunctions } = await mermaid.render(
          `clubstock-diagram-${index}`, block.textContent
        );
        const diagram = document.createElement('div');
        diagram.className = 'mermaid';
        diagram.style.overflowX = 'auto';
        diagram.innerHTML = svg;
        const pre = block.closest('pre');
        const wrapper = pre.closest('div.highlighter-rouge');
        (wrapper || pre).replaceWith(diagram);
        bindFunctions?.(diagram);
      } catch (error) {
        // Keep the original definition readable if one diagram is invalid.
        console.error('Unable to render Mermaid diagram:', error);
      }
    }
  } catch (error) {
    // A blocked CDN or offline browser should leave the code blocks intact.
    console.error('Unable to load Mermaid:', error);
  }
}
