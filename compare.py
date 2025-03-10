import sys
import argparse
from lxml import etree
from copy import deepcopy
import difflib


def normalize_ids(root):
    """
    Remove all 'id' attributes from the XML tree to ignore them in comparison.
    Returns a copy of the tree with ids removed.
    """
    tree_copy = deepcopy(root)
    for element in tree_copy.xpath("//*[@id]"):
        del element.attrib["id"]
    return tree_copy


def compare_xml_files(file1_path, file2_path, show_diff=False):
    """
    Compare two XML files, ignoring @id attributes
    """
    try:
        # Parse XML files
        parser = etree.XMLParser(remove_blank_text=True)
        tree1 = etree.parse(file1_path, parser)
        tree2 = etree.parse(file2_path, parser)

        # Remove id attributes for comparison
        norm_tree1 = normalize_ids(tree1.getroot())
        norm_tree2 = normalize_ids(tree2.getroot())

        # Convert to string represenation for comparison
        xml_str1 = etree.tostring(
            norm_tree1, encoding="utf-8", pretty_print=True
        ).decode("utf-8")
        xml_str2 = etree.tostring(
            norm_tree2, encoding="utf-8", pretty_print=True
        ).decode("utf-8")

        if xml_str1 == xml_str2:
            print(f"The XML files are identical when ignoring @id attributes.")
            return True
        else:
            print(f"The XML files are different (ignoring @id attributes).")
            if show_diff:
                diff = difflib.unified_diff(
                    xml_str1.splitlines(True),
                    xml_str2.splitlines(True),
                    fromfile=file1_path,
                    tofile=file2_path,
                )
                print("".join(diff))
            return False
    except Exception as e:
        print(f"Error comparing XML files: {str(e)}")
        return False


def main():
    parser = argparse.ArgumentParser(
        description="Compare two XML files ignoring @id attributes"
    )
    parser.add_argument("file1", help="First XML file")
    parser.add_argument("file2", help="Second XML file")
    parser.add_argument(
        "--diff", "-d", action="store_true", help="Show detailed differences"
    )

    args = parser.parse_args()

    same = compare_xml_files(args.file1, args.file2, args.diff)
    sys.exit(0 if same else 1)


if __name__ == "__main__":
    main()
