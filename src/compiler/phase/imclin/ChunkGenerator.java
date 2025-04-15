package compiler.phase.imclin;

import compiler.phase.abstr.AST;

public class ChunkGenerator implements AST.FullVisitor<Object, Object> {
	private CodeChunkGenerator codeChunkGenerator = new CodeChunkGenerator();
	private DataChunkGenerator dataChunkGenerator = new DataChunkGenerator();

	@Override
	public Object visit(AST.Nodes<? extends AST.Node> nodes, Object arg) {
		nodes.accept(dataChunkGenerator, null);
		nodes.accept(codeChunkGenerator, null);
		return null;
	}
}
